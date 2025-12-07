package com.billing.simple.billsoft.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.Base64; // (can be removed if not used anywhere else)

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.billing.simple.billsoft.constants.LicensingConstants;
import com.billing.simple.billsoft.entities.AppLicense;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppLicenseRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final FirmDetailsRepository firmRepo;
    private final AppLicenseRepository licenseRepo;

    public AuthService(FirmDetailsRepository firmRepo,
                       AppLicenseRepository licenseRepo) {
        this.firmRepo = firmRepo;
        this.licenseRepo = licenseRepo;
    }

    /* ================================================================================== */
    /* LICENSE KEYS (LITERAL, NO ENCRYPTION)                                              */
    /* ================================================================================== */

    private static final String KEY_1Y_PREMIUM   = "INV-1Y-PREMIUM-2025-RY";
    private static final String KEY_3Y_PREMIUM   = "INV-3Y-PREMIUM-2025-RY";
    private static final String KEY_LIFE_PREMIUM = "INV-LIFE-PREMIUM-2025-RY";
    private static final String KEY_TEST_2MIN    = "INV-TEST-2MIN-2025-RY";

    // We will **store expiry as plain text** in AppLicense. No AES now.
    // Column name is still license_expiry_encrypted for schema compatibility,
    // but content will be ISO-8601 Instant string, e.g. "2026-01-06T12:27:37.663118700Z"


    /* ================================================================================== */
    /* LOGIN LOCKOUT CONFIG                                                               */
    /* ================================================================================== */

    private static final int MAX_FAIL     = 7;
    private static final int WARN_FAIL_1  = 3;
    private static final int WARN_FAIL_2  = 5;
    private static final Duration LOCK_1  = Duration.ofMinutes(5);
    private static final Duration LOCK_2  = Duration.ofMinutes(30);

    // Developer reset lockout config
    private static final int      RESET_MAX_FAIL       = 10;
    private static final Duration RESET_LOCK_DURATION  = Duration.ofHours(3);

    // Developer master key hash (for support reset)
    private static final String DEV_RESET_KEY_HASH =
            "0CDB3704A8993071B98A9F4803DED2B39C54BE9DD0C688380896FB338AE1FACB";

    /* ================================================================================== */
    /* DTOs                                                                               */
    /* ================================================================================== */

    public static class LoginRequest {
        public String loginId;
        public String password;
        public String activationKey;
    }

    public static class LoginResult {
        public boolean success;
        public String message;
        public Long firmId;
        public String firmName;

        public boolean locked;
        public String lockReason;
        public Instant unlockAt;
        public int remainingAttempts;
        public int maxAttempts;
        public String securityLevel;
        public boolean showForgotPassword = true;

        public boolean trial;
        public boolean licenseOk;
        public String licenseLevel;
        public String licenseStatus;
        public Instant licenseExpiryAt;
    }

    public static class RegisterRequest {
        public String loginId;
        public String password;
    }

    public static class RegisterResult {
        public boolean success;
        public String message;
        public Long firmId;
    }

    public static class DeveloperResetRequest {
        public String loginId;
        public String developerKey;
        public String newPassword;
    }

    public static class SimpleResult {
        public boolean success;
        public String message;
    }

    public static class DevResetValidationRequest {
        public String loginId;
        public String developerKey;
    }

    public static class DevResetValidationResult {
        public boolean valid;
        public boolean locked;
        public String message;
        public Instant unlockAt;
        public int attemptsLeft;
        public int maxAttempts;
    }

    /* ================================================================================== */
    /* LICENSE ROW HELPER (GLOBAL LICENSE ROW id=1)                                       */
    /* ================================================================================== */

    private AppLicense ensureLicense() {
        return licenseRepo.findById(1L).orElseGet(() -> {
            AppLicense lic = new AppLicense();
            lic.setId(1L);
            lic.setLicenseLevel(LicensingConstants.LICENSE_TRIAL);

            Instant exp = Instant.now()
                    .plus(Duration.ofDays(LicensingConstants.TRIAL_DAYS));

            // store expiry as plain ISO string instead of encrypted value
            lic.setLicenseExpiryEncrypted(instantToLiteral(exp));
            lic.setTrialStartDate(LocalDate.now());
            return licenseRepo.save(lic);
        });
    }

    /* ================================================================================== */
    /* REGISTRATION – SINGLE FIRM FOR NOW                                                 */
    /* ================================================================================== */

    @Transactional
    public RegisterResult register(RegisterRequest req) {
        RegisterResult out = new RegisterResult();
        out.success = false;

        if (!StringUtils.hasText(req.loginId) || !StringUtils.hasText(req.password)) {
            out.message = "Login ID and password are required.";
            return out;
        }
        if (req.password.length() < 6) {
            out.message = "Password must be at least 6 characters.";
            return out;
        }

        String loginId = req.loginId.trim();

        // unique loginId
        FirmDetails existing = firmRepo.findByLoginIdIgnoreCase(loginId).orElse(null);
        if (existing != null) {
            out.success = false;
            out.message = "This Login ID is already in use. Please choose a different one.";
            out.firmId = existing.getId();
            return out;
        }

        FirmDetails f = new FirmDetails();
        f.setLoginId(loginId);
        f.setPasswordHash(hashPassword(req.password, loginId));
        f.setFailedLoginAttempts(0);
        f.setLockoutUntil(null);
        f.setResetFailCount(0);
        f.setResetLockedUntil(null);

        firmRepo.save(f);

        // ensure global license (trial) exists
        ensureLicense();

        out.success = true;
        out.message = "Account created.";
        out.firmId = f.getId();
        return out;
    }

    /* ================================================================================== */
    /* LOGIN & ACTIVATION                                                                 */
    /* ================================================================================== */

    @Transactional
    public LoginResult login(LoginRequest req) {
        LoginResult out = new LoginResult();
        out.maxAttempts = MAX_FAIL;

        String loginId = nullSafe(req.loginId).trim();
        FirmDetails f = firmRepo.findByLoginIdIgnoreCase(loginId).orElse(null);

        if (f == null || !StringUtils.hasText(f.getLoginId())) {
            out.success = false;
            out.message = "No account found for given Login ID.";
            out.remainingAttempts = MAX_FAIL;
            return out;
        }

        out.firmId = f.getId();
        out.firmName = f.getFirmName();
        out.remainingAttempts = MAX_FAIL - safeAttempts(f);

        // permanent lock
        if (safeAttempts(f) >= MAX_FAIL) {
            out.locked = true;
            out.success = false;
            out.securityLevel = "FROZEN";
            out.lockReason = "Account permanently locked. Contact support.";
            out.remainingAttempts = 0;
            return out;
        }

        // temporary lock
        if (f.getLockoutUntil() != null &&
                LocalDateTime.now().isBefore(f.getLockoutUntil())) {

            out.locked = true;
            out.securityLevel = "TEMP_LOCK";
            out.lockReason = "Account temporarily locked.";
            out.unlockAt = f.getLockoutUntil()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
            out.success = false;
            return out;
        }

        // wrong credentials
        if (!f.getLoginId().equalsIgnoreCase(loginId)
                || !f.getPasswordHash().equals(
                hashPassword(nullSafe(req.password), loginId))) {

            handleWrongPassword(out, f);
            firmRepo.save(f);
            return out;
        }

        // successful login → reset security
        f.setFailedLoginAttempts(0);
        f.setLockoutUntil(null);

        // apply activation key (if any)
        if (StringUtils.hasText(req.activationKey)) {
            applyActivation(req.activationKey.trim());
        }

        // check license
        evaluateLicense(out);

        firmRepo.save(f);
        return out;
    }

    private void handleWrongPassword(LoginResult out, FirmDetails f) {
        int fail = safeAttempts(f) + 1;
        f.setFailedLoginAttempts(fail);

        out.remainingAttempts = Math.max(0, MAX_FAIL - fail);
        out.success = false;
        out.showForgotPassword = true;

        if (fail >= MAX_FAIL) {
            out.locked = true;
            out.securityLevel = "FROZEN";
            out.lockReason = "Account permanently locked. Contact support.";
            out.message = out.lockReason;
            f.setLockoutUntil(LocalDateTime.now().plusYears(100));

        } else if (fail >= WARN_FAIL_2) {
            out.locked = true;
            out.securityLevel = "LOCK_30";
            out.lockReason = "Too many failed attempts. Locked for 30 minutes.";
            out.message = out.lockReason;
            f.setLockoutUntil(LocalDateTime.now().plus(LOCK_2));

        } else if (fail >= WARN_FAIL_1) {
            out.locked = true;
            out.securityLevel = "LOCK_5";
            out.lockReason = "Too many failed attempts. Locked for 5 minutes.";
            out.message = out.lockReason;
            f.setLockoutUntil(LocalDateTime.now().plus(LOCK_1));

        } else {
            out.locked = false;
            out.securityLevel = "SAFE";
            out.lockReason = "Invalid login ID or password.";
            out.message = out.lockReason;
        }
    }

    /* ================================================================================== */
    /* LICENSE EVALUATION (NO ENCRYPTION, LITERAL EXPIRY)                                 */
    /* ================================================================================== */

    private void evaluateLicense(LoginResult out) {
        AppLicense lic = ensureLicense();
        Instant now = Instant.now();
        Instant expiry = literalToInstant(lic.getLicenseExpiryEncrypted());

        // self-heal if somehow null or level missing
        if (expiry == null || !StringUtils.hasText(lic.getLicenseLevel())) {
            expiry = now.plus(Duration.ofDays(LicensingConstants.TRIAL_DAYS));
            lic.setLicenseLevel(LicensingConstants.LICENSE_TRIAL);
            lic.setLicenseExpiryEncrypted(instantToLiteral(expiry));
            if (lic.getTrialStartDate() == null) {
                lic.setTrialStartDate(LocalDate.now());
            }
            licenseRepo.save(lic);
        }

        out.licenseExpiryAt = expiry;
        out.licenseLevel = lic.getLicenseLevel();
        out.trial = LicensingConstants.LICENSE_TRIAL.equals(lic.getLicenseLevel());

        if (expiry == null || now.isAfter(expiry)) {
            out.licenseOk = false;
            out.success = false;
            out.message = "License expired — please enter activation key.";
            out.licenseStatus = "Expired";
        } else {
            out.licenseOk = true;
            out.success = true;
            out.message = "Login successful.";
            out.licenseStatus = out.trial ? "Trial active" : "Premium active";
        }
    }

    /* ================================================================================== */
    /* ACTIVATION LOGIC – LITERAL KEYS                                                    */
    /* ================================================================================== */

    private void applyActivation(String key) {
        String k = nullSafe(key).trim();

        if (!StringUtils.hasText(k)) return;

        AppLicense lic = ensureLicense();

        Instant now = Instant.now();
        Instant currentExp = literalToInstant(lic.getLicenseExpiryEncrypted());
        Instant base = (currentExp != null && currentExp.isAfter(now))
                ? currentExp
                : now;

        Instant newExpiry = null;
        String newLevel = lic.getLicenseLevel();

        if (k.equalsIgnoreCase(KEY_1Y_PREMIUM)) {
            newLevel = LicensingConstants.LICENSE_PREMIUM;
            newExpiry = base.plus(Duration.ofDays(365));

        } else if (k.equalsIgnoreCase(KEY_3Y_PREMIUM)) {
            newLevel = LicensingConstants.LICENSE_PREMIUM;
            newExpiry = base.plus(Duration.ofDays(365 * 3L));

        } else if (k.equalsIgnoreCase(KEY_LIFE_PREMIUM)) {
            newLevel = LicensingConstants.LICENSE_PREMIUM;
            newExpiry = base.plus(Duration.ofDays(365 * 50L));

        } else if (k.equalsIgnoreCase(KEY_TEST_2MIN)) {
            newLevel = LicensingConstants.LICENSE_TEST;
            newExpiry = now.plus(Duration.ofMinutes(2));
        } else {
            // invalid key → do nothing; caller will show error via message logic in controller/DTO
            return;
        }

        lic.setLicenseLevel(newLevel);
        lic.setLicenseExpiryEncrypted(instantToLiteral(newExpiry));
        licenseRepo.save(lic);
    }

    /* ================================================================================== */
    /* SUPPORT RESET (unchanged, still using DEV_RESET_KEY_HASH)                          */
    /* ================================================================================== */

    @Transactional
    public DevResetValidationResult validateResetDev(DevResetValidationRequest req) {
        DevResetValidationResult out = new DevResetValidationResult();
        out.valid = false;
        out.maxAttempts = RESET_MAX_FAIL;

        if (!StringUtils.hasText(req.loginId) || !StringUtils.hasText(req.developerKey)) {
            out.message = "Login ID and Secure Reset Key are required.";
            return out;
        }

        FirmDetails f = firmRepo.findByLoginIdIgnoreCase(req.loginId.trim()).orElse(null);
        if (f == null) {
            out.message = "Account not found for given Login ID.";
            return out;
        }

        int used = safeResetAttempts(f);
        out.attemptsLeft = Math.max(0, RESET_MAX_FAIL - used);

        if (f.getResetLockedUntil() != null &&
                LocalDateTime.now().isBefore(f.getResetLockedUntil())) {
            out.locked = true;
            out.message = "Support reset temporarily locked.";
            out.unlockAt = f.getResetLockedUntil()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
            out.attemptsLeft = 0;
            return out;
        }

        String incomingHash = sha256(req.developerKey.trim());
        if (!DEV_RESET_KEY_HASH.equalsIgnoreCase(incomingHash)) {
            recordResetFail(f);
            firmRepo.save(f);

            used = safeResetAttempts(f);
            out.attemptsLeft = Math.max(0, RESET_MAX_FAIL - used);

            if (f.getResetLockedUntil() != null &&
                    LocalDateTime.now().isBefore(f.getResetLockedUntil())) {
                out.locked = true;
                out.message = "Too many attempts — try again later.";
                out.unlockAt = f.getResetLockedUntil()
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
                out.attemptsLeft = 0;
            } else {
                out.message = "Incorrect Secure Reset Key.";
            }
            return out;
        }

        clearResetSecurity(f);
        firmRepo.save(f);

        out.valid = true;
        out.message = "Verified. You can proceed.";
        out.attemptsLeft = RESET_MAX_FAIL;
        return out;
    }

    @Transactional
    public SimpleResult resetPasswordDev(DeveloperResetRequest req) {
        SimpleResult out = new SimpleResult();
        out.success = false;

        if (!StringUtils.hasText(req.loginId)) return fail(out, "Login ID is required.");
        if (!StringUtils.hasText(req.developerKey)) return fail(out, "Secure reset key required.");
        if (!StringUtils.hasText(req.newPassword) || req.newPassword.length() < 6)
            return fail(out, "New password must be at least 6 characters.");

        FirmDetails f = firmRepo.findByLoginIdIgnoreCase(req.loginId.trim()).orElse(null);
        if (f == null) return fail(out, "Account not found.");

        if (f.getResetLockedUntil() != null &&
                LocalDateTime.now().isBefore(f.getResetLockedUntil())) {
            return fail(out, "Support reset locked temporarily. Try later.");
        }

        if (!sha256(req.developerKey.trim()).equalsIgnoreCase(DEV_RESET_KEY_HASH)) {
            recordResetFail(f);
            firmRepo.save(f);
            return fail(out, "Invalid Secure Reset Key.");
        }

        f.setPasswordHash(hashPassword(req.newPassword, f.getLoginId()));
        f.setFailedLoginAttempts(0);
        f.setLockoutUntil(null);
        clearResetSecurity(f);

        firmRepo.save(f);

        out.success = true;
        out.message = "Password updated successfully.";
        return out;
    }

    /* ================================================================================== */
    /* HELPER UTILITIES                                                                   */
    /* ================================================================================== */

    private SimpleResult fail(SimpleResult out, String msg) {
        out.success = false;
        out.message = msg;
        return out;
    }

    private int safeAttempts(FirmDetails f) {
        return f.getFailedLoginAttempts() == null ? 0 : f.getFailedLoginAttempts();
    }

    private int safeResetAttempts(FirmDetails f) {
        return f.getResetFailCount() == null ? 0 : f.getResetFailCount();
    }

    private void recordResetFail(FirmDetails f) {
        int fail = safeResetAttempts(f) + 1;
        f.setResetFailCount(fail);
        if (fail >= RESET_MAX_FAIL) {
            f.setResetLockedUntil(LocalDateTime.now().plus(RESET_LOCK_DURATION));
        }
    }

    private void clearResetSecurity(FirmDetails f) {
        f.setResetFailCount(0);
        f.setResetLockedUntil(null);
    }

    private String hashPassword(String pwd, String loginId) {
        String base = "BSOFT|" +
                nullSafe(loginId).toLowerCase() +
                "|" + nullSafe(pwd);
        return sha256(base);
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02X", x));
            return sb.toString();
        } catch (Exception e) {
            log.error("SHA-256 error", e);
            return null;
        }
    }

    /* ---------- LITERAL EXPIRY HELPERS (NO AES) ---------- */

    private String instantToLiteral(Instant exp) {
        if (exp == null) return null;
        // ISO string is human readable and easy to debug
        return exp.toString();
    }

    private Instant literalToInstant(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            // try ISO-8601 first
            return Instant.parse(value.trim());
        } catch (Exception e) {
            try {
                // fallback: maybe it is stored as epoch millis string
                long epoch = Long.parseLong(value.trim());
                return Instant.ofEpochMilli(epoch);
            } catch (Exception e2) {
                log.warn("Failed to parse license expiry literal: {}", value);
                return null;
            }
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
