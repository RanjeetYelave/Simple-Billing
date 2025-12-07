package com.billing.simple.billsoft.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;

import jakarta.transaction.Transactional;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final FirmDetailsRepository firmRepo;

    public AuthService(FirmDetailsRepository firmRepo) {
        this.firmRepo = firmRepo;
    }

    // ---------------- CONSTANTS ----------------

    // AES key + IV (16 bytes each)
    private static final String AES_KEY = "BSOFT-LIC-2025!!";  // 16 bytes
    private static final String AES_IV  = "LIC-INIT-2025!!";  // 16 bytes

    // developer reset soft lock (support-only)
    private static final int RESET_MAX_FAIL = 10;
    private static final Duration RESET_LOCK_DURATION = Duration.ofHours(3);

    private static final long TRIAL_DAYS = 30L;

    // login lock / attempts
    private static final int MAX_FAIL      = 7;   // after this → permanent lock
    private static final int WARN_FAIL_1   = 3;   // 3rd wrong → 5 min lock
    private static final int WARN_FAIL_2   = 5;   // 5th wrong → 30 min lock

    private static final Duration LOCK_1 = Duration.ofMinutes(5);
    private static final Duration LOCK_2 = Duration.ofMinutes(30);

    // license levels
    private static final String LICENSE_TRIAL        = "TRIAL";
    private static final String LICENSE_PREMIUM      = "PREMIUM";
    private static final String LICENSE_PREMIUM_TEST = "PREMIUM_TEST";

    // developer master key (SHA-256 of "RANJEET-ADMIN-RESET-2025")
    private static final String DEV_RESET_KEY_HASH =
            "0CDB3704A8993071B98A9F4803DED2B39C54BE9DD0C688380896FB338AE1FACB";

    // Activation key hashes
    private static final String KEY_1Y_HASH =
            "9444E6D43B94AD97F7AFDC7D9EEC0FEE2D3B570D5505265D0058E52BE6C29795";
    private static final String KEY_3Y_HASH =
            "FD45AF8044174C891A2D776FD236D0B3ED402FFB5597CDF92875161B47677019";
    private static final String KEY_LIFE_HASH =
            "46C980C96774C909C3DCCD9647FCE69845BD5B1D8655C2F89FC785B1A34777C3";
    private static final String KEY_TEST_HASH =
            "6F1FAB01A6195F6B553B3CA078F0F171B96568CF3160B60EA28EBFCBECF535FE";

    // ---------------- DTOs ----------------

    public static class LoginRequest {
        public String loginId;
        public String password;
        public String activationKey; // optional
    }

    public static class LoginResult {
        public boolean success;
        public String message;

        public Long firmId;
        public String firmName;

        // security / lockout
        public boolean locked;
        public String lockReason;
        public Instant unlockAt;
        public int remainingAttempts;
        public int maxAttempts;
        public String securityLevel;
        public boolean showForgotPassword = true;

        // license
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

    // 🔹 New — validate Step-1 request
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

    // -------------------------------------------------------------------------
    // REGISTER
    // -------------------------------------------------------------------------

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

        // Single-firm for now → id = 1
        FirmDetails f = firmRepo.findById(1L).orElse(new FirmDetails());
        f.setId(1L);

        if (StringUtils.hasText(f.getLoginId())
                && f.getLoginId().equalsIgnoreCase(req.loginId.trim())) {
            out.success = true;
            out.message = "Account already exists. Please login.";
            out.firmId = f.getId();
            return out;
        }

        f.setLoginId(req.loginId.trim());
        f.setPasswordHash(hashPassword(req.password, req.loginId));

        // initialize trial license
        Instant expiry = Instant.now().plus(Duration.ofDays(TRIAL_DAYS));
        f.setLicenseLevel(LICENSE_TRIAL);
        f.setLicenseExpiryEncrypted(enc(expiry));
        f.setTrialStartDate(LocalDate.now());

        // reset login security fields
        f.setFailedLoginAttempts(0);
        f.setLockoutUntil(null);

        // reset developer reset security
        clearResetSecurity(f);

        firmRepo.save(f);

        out.success = true;
        out.message = "Account created.";
        out.firmId = f.getId();
        return out;
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    @Transactional
    public LoginResult login(LoginRequest req) {
        LoginResult out = new LoginResult();
        out.maxAttempts = MAX_FAIL;

        FirmDetails f = firmRepo.findById(1L).orElse(null);
        if (f == null || !StringUtils.hasText(f.getLoginId())) {
            out.success = false;
            out.message = "No account found. Please create an account.";
            out.remainingAttempts = MAX_FAIL;
            return out;
        }

        out.firmId = f.getId();
        out.firmName = f.getFirmName();
        out.remainingAttempts = MAX_FAIL - safeAttempts(f);

        // Permanent lock
        if (safeAttempts(f) >= MAX_FAIL) {
            out.locked = true;
            out.securityLevel = "FROZEN";
            out.lockReason = "Account permanently locked. Please contact support.";
            out.remainingAttempts = 0;
            out.success = false;
            return out;
        }

        // Temp lock
        if (f.getLockoutUntil() != null &&
                LocalDateTime.now().isBefore(f.getLockoutUntil())) {

            out.locked = true;
            out.securityLevel = "TEMP_LOCK";
            out.lockReason = "Account temporarily locked.";
            out.unlockAt = f.getLockoutUntil().atZone(ZoneId.systemDefault()).toInstant();
            out.remainingAttempts = MAX_FAIL - safeAttempts(f);
            out.success = false;
            return out;
        }

        // Wrong credentials
        if (!f.getLoginId().equalsIgnoreCase(nullSafe(req.loginId)) ||
                !f.getPasswordHash().equals(hashPassword(nullSafe(req.password), req.loginId))) {

            handleWrongPassword(out, f);
            firmRepo.save(f);
            return out;
        }

        // Success → clear login lockouts
        f.setFailedLoginAttempts(0);
        f.setLockoutUntil(null);

        // Activation key (if provided)
        if (StringUtils.hasText(req.activationKey)) {
            applyActivation(f, req.activationKey.trim());
        }

        // License evaluation
        evaluateLicense(out, f);

        firmRepo.save(f);
        return out;
    }

    private int safeAttempts(FirmDetails f) {
        return f.getFailedLoginAttempts() == null ? 0 : f.getFailedLoginAttempts();
    }

    private void handleWrongPassword(LoginResult out, FirmDetails f) {
        int fail = safeAttempts(f) + 1;
        f.setFailedLoginAttempts(fail);

        out.maxAttempts = MAX_FAIL;
        out.remainingAttempts = Math.max(0, MAX_FAIL - fail);
        out.success = false;
        out.showForgotPassword = true;

        if (fail >= MAX_FAIL) {
            out.locked = true;
            out.securityLevel = "FROZEN";
            out.lockReason = "Account permanently locked. Please contact support.";
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

    private void evaluateLicense(LoginResult out, FirmDetails f) {
        Instant now = Instant.now();
        Instant expiry = dec(f.getLicenseExpiryEncrypted());

        if (expiry == null || !StringUtils.hasText(f.getLicenseLevel())) {
            expiry = now.plus(Duration.ofDays(TRIAL_DAYS));
            f.setLicenseLevel(LICENSE_TRIAL);
            f.setLicenseExpiryEncrypted(enc(expiry));
            if (f.getTrialStartDate() == null) {
                f.setTrialStartDate(LocalDate.now());
            }
        }

        out.licenseExpiryAt = expiry;
        out.licenseLevel = f.getLicenseLevel();
        out.trial = LICENSE_TRIAL.equals(f.getLicenseLevel());

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

    // -------------------------------------------------------------------------
    // DEVELOPER RESET VALIDATION (Step 1)
    // -------------------------------------------------------------------------

    @Transactional
    public DevResetValidationResult validateResetDev(DevResetValidationRequest req) {
        DevResetValidationResult out = new DevResetValidationResult();
        out.valid = false;
        out.locked = false;
        out.maxAttempts = RESET_MAX_FAIL;
        out.attemptsLeft = RESET_MAX_FAIL;

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

        // soft lock check
        if (f.getResetLockedUntil() != null &&
                LocalDateTime.now().isBefore(f.getResetLockedUntil())) {

            out.locked = true;
            out.message = "Support reset is temporarily locked due to repeated incorrect attempts.";
            out.unlockAt = f.getResetLockedUntil().atZone(ZoneId.systemDefault()).toInstant();
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
                out.message = "Too many incorrect attempts. Support reset is locked for a few hours.";
                out.unlockAt = f.getResetLockedUntil().atZone(ZoneId.systemDefault()).toInstant();
                out.attemptsLeft = 0;
            } else if (used >= 7) {
                out.message = "Multiple incorrect attempts. Please stop guessing.";
            } else {
                out.message = "Incorrect Secure Reset Key. Please confirm with support.";
            }

            return out;
        }

        // success -> clear reset security
        clearResetSecurity(f);
        firmRepo.save(f);

        out.valid = true;
        out.message = "Details verified. You can proceed.";
        out.attemptsLeft = RESET_MAX_FAIL;
        return out;
    }

    private int safeResetAttempts(FirmDetails f) {
        return (f.getResetFailCount() == null) ? 0 : f.getResetFailCount();
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

    // -------------------------------------------------------------------------
    // DEVELOPER RESET EXECUTION (Step 2)
    // -------------------------------------------------------------------------

    @Transactional
    public SimpleResult resetPasswordDev(DeveloperResetRequest req) {
        SimpleResult out = new SimpleResult();
        out.success = false;

        if (!StringUtils.hasText(req.loginId)) return fail(out,"Login ID is required.");
        if (!StringUtils.hasText(req.developerKey)) return fail(out,"Internal reset key is required.");
        if (!StringUtils.hasText(req.newPassword) || req.newPassword.length() < 6)
            return fail(out,"New password must be at least 6 characters.");

        FirmDetails f = firmRepo.findByLoginIdIgnoreCase(req.loginId.trim()).orElse(null);
        if (f == null) return fail(out,"Account not found for given Login ID.");

        // soft lock check
        if (f.getResetLockedUntil() != null &&
                LocalDateTime.now().isBefore(f.getResetLockedUntil())) {
            return fail(out,"Support reset locked temporarily. Try later.");
        }

        // validate master key
        if (!sha256(req.developerKey.trim()).equalsIgnoreCase(DEV_RESET_KEY_HASH)) {
            recordResetFail(f);
            firmRepo.save(f);

            if (f.getResetLockedUntil() != null &&
                    LocalDateTime.now().isBefore(f.getResetLockedUntil())) {
                return fail(out,"Too many incorrect reset attempts. Reset locked temporarily.");
            }
            return fail(out,"Invalid internal reset key.");
        }

        // success → reset everything
        f.setPasswordHash(hashPassword(req.newPassword, f.getLoginId()));
        f.setFailedLoginAttempts(0);
        f.setLockoutUntil(null);
        clearResetSecurity(f);

        firmRepo.save(f);

        out.success = true;
        out.message = "Password updated successfully.";
        return out;
    }

    private SimpleResult fail(SimpleResult out, String msg) {
        out.success = false;
        out.message = msg;
        return out;
    }

    // -------------------------------------------------------------------------
    // LICENSE ACTIVATION
    // -------------------------------------------------------------------------

    private void applyActivation(FirmDetails f, String key) {
        String h = sha256(key);
        Instant now = Instant.now();
        Instant currentExp = dec(f.getLicenseExpiryEncrypted());
        Instant base = (currentExp != null && currentExp.isAfter(now)) ? currentExp : now;

        if (h.equalsIgnoreCase(KEY_1Y_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM);
            f.setLicenseExpiryEncrypted(enc(base.plus(Duration.ofDays(365))));

        } else if (h.equalsIgnoreCase(KEY_3Y_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM);
            f.setLicenseExpiryEncrypted(enc(base.plus(Duration.ofDays(365 * 3L))));

        } else if (h.equalsIgnoreCase(KEY_LIFE_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM);
            f.setLicenseExpiryEncrypted(enc(base.plus(Duration.ofDays(365 * 50L))));

        } else if (h.equalsIgnoreCase(KEY_TEST_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM_TEST);
            f.setLicenseExpiryEncrypted(enc(now.plus(Duration.ofMinutes(2))));
        }
    }

    // -------------------------------------------------------------------------
    // HASH + CRYPTO HELPERS
    // -------------------------------------------------------------------------

    private String hashPassword(String pwd, String loginId) {
        String base = "BSOFT|" + nullSafe(loginId).toLowerCase() + "|" + nullSafe(pwd);
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

    private String enc(Instant exp) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(AES_IV.getBytes(StandardCharsets.UTF_8)));
            String payload = String.valueOf(exp.toEpochMilli());
            return Base64.getEncoder()
                    .encodeToString(c.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Encrypt failed", e);
            return null;
        }
    }

    private Instant dec(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(AES_IV.getBytes(StandardCharsets.UTF_8)));
            byte[] plain = c.doFinal(Base64.getDecoder().decode(s));
            long epoch = Long.parseLong(new String(plain, StandardCharsets.UTF_8).trim());
            return Instant.ofEpochMilli(epoch);
        } catch (Exception e) {
            log.warn("Decrypt failed – treating as expired");
            return null;
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
