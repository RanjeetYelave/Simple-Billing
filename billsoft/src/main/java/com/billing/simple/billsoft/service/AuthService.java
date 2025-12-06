package com.billing.simple.billsoft.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final FirmDetailsRepository firmRepo;

    public AuthService(FirmDetailsRepository firmRepo) {
        this.firmRepo = firmRepo;
    }

    // ---------------- CONSTANTS ----------------

    // Password hashing salt (internal)
    private static final String PASSWORD_SALT = "BILLSOFT-PWD-SALT-2025";

    // Developer reset key (SHA-256("RANJEET-ADMIN-RESET-2025"))
    private static final String DEV_RESET_KEY_HASH =
            "0CDB3704A8993071B98A9F4803DED2B39C54BE9DD0C688380896FB338AE1FACB";

    // Activation keys (SHA-256 of raw string)
    private static final String ACTIVATION_1Y_HASH =
            "9444E6D43B94AD97F7AFDC7D9EEC0FEE2D3B570D5505265D0058E52BE6C29795";
    private static final String ACTIVATION_3Y_HASH =
            "FD45AF8044174C891A2D776FD236D0B3ED402FFB5597CDF92875161B47677019";
    private static final String ACTIVATION_LIFE_HASH =
            "46C980C96774C909C3DCCD9647FCE69845BD5B1D8655C2F89FC785B1A34777C3";
    private static final String ACTIVATION_TEST_HASH =
            "6F1FAB01A6195F6B553B3CA078F0F171B96568CF3160B60EA28EBFCBECF535FE";

    // License types
    private static final String LICENSE_TRIAL = "TRIAL";
    private static final String LICENSE_PREMIUM = "PREMIUM";
    private static final String LICENSE_PREMIUM_TEST = "PREMIUM_TEST";

    // Free trial duration
    private static final long TRIAL_DAYS = 30;

    /**
     * Secret used ONLY for signing the expiry string.
     * (Not used as AES key anymore, so length doesn't matter.)
     */
    private static final String LICENSE_SECRET_KEY = "BILLSOFT-LICENSE-SIGN-2025";

    // Lockout thresholds
    private static final int FIRST_LOCK_THRESHOLD = 3;   // 3 attempts → 5 minutes
    private static final int SECOND_LOCK_THRESHOLD = 6;  // 6 attempts → 30 minutes
    private static final int FINAL_LOCK_THRESHOLD = 7;   // 7+ → effectively permanent

    // ---------------- DTOs ----------------

    public static class LoginRequest {
        public String loginId;
        public String password;
        public String activationKey; // optional
    }

    public static class RegisterRequest {
        public String loginId;
        public String password;
    }

    public static class LoginResult {
        public boolean success;
        public String message;

        public Long firmId;
        public String firmName;

        public boolean locked;
        public String lockReason;
        public LocalDateTime lockoutUntil;
        public boolean showForgotPassword;

        public boolean licenseOk;
        public String licenseLevel;
        public String licenseStatus;
        public Instant licenseExpiryAt;
        public boolean trial;
    }

    public static class RegisterResult {
        public boolean success;
        public String message;
        public Long firmId;
    }

    public static class DeveloperResetRequest {
        public Long firmId;
        public String developerKey;
        public String newPassword;
    }

    public static class SimpleResult {
        public boolean success;
        public String message;
    }

    // ---------------- REGISTER ----------------

    @Transactional
    public RegisterResult register(RegisterRequest req) {
        RegisterResult res = new RegisterResult();

        if (!StringUtils.hasText(req.loginId) || !StringUtils.hasText(req.password)) {
            res.success = false;
            res.message = "Login ID and password are required.";
            return res;
        }
        if (req.password.length() < 6) {
            res.success = false;
            res.message = "Password must be at least 6 characters.";
            return res;
        }

        FirmDetails firm = firmRepo.findById(1L).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setId(1L);
            return firmRepo.save(f);
        });

        // Prevent duplicate same loginId
        if (firm.getLoginId() != null &&
            firm.getLoginId().equalsIgnoreCase(req.loginId.trim())) {
            res.success = false;
            res.message = "Account already exists. Please login.";
            res.firmId = firm.getId();
            return res;
        }

        String loginId = req.loginId.trim();
        firm.setLoginId(loginId);
        firm.setPasswordHash(hashPassword(req.password, loginId));

        // Fresh trial for new account
        LocalDate today = LocalDate.now();
        Instant expiry = Instant.now().plus(Duration.ofDays(TRIAL_DAYS));

        firm.setTrialStartDate(today);
        firm.setLicenseLevel(LICENSE_TRIAL);
        firm.setLicenseExpiryEncrypted(encryptExpiry(expiry));

        firm.setFailedLoginAttempts(0);
        firm.setLockoutUntil(null);

        firmRepo.save(firm);

        log.info("REGISTER completed successfully, loginId={}, trial expiry={}",
                loginId, expiry);

        res.success = true;
        res.message = "Account created successfully.";
        res.firmId = firm.getId();
        return res;
    }

    // ---------------- LOGIN ----------------

    @Transactional
    public LoginResult login(LoginRequest req) {
        LoginResult out = new LoginResult();

        log.info("LOGIN attempt loginId={}", req.loginId);

        FirmDetails firm = firmRepo.findById(1L).orElse(null);
        if (firm == null || !StringUtils.hasText(firm.getLoginId())) {
            out.success = false;
            out.message = "No account exists. Please create one.";
            return out;
        }

        out.firmId = firm.getId();
        out.firmName = firm.getFirmName();

        // Lockout check
        LocalDateTime now = LocalDateTime.now();
        if (firm.getLockoutUntil() != null &&
            now.isBefore(firm.getLockoutUntil())) {

            out.success = false;
            out.locked = true;
            out.lockReason = "Too many failed attempts. Try again later.";
            out.lockoutUntil = firm.getLockoutUntil();
            out.message = out.lockReason;
            return out;
        }

        // Login ID check (no lockout increment)
        if (!firm.getLoginId().equalsIgnoreCase(
                req.loginId == null ? "" : req.loginId.trim())) {

            out.success = false;
            out.message = "Invalid login ID or password.";
            return out;
        }

        // Password check
        String incomingHash = hashPassword(
                req.password == null ? "" : req.password,
                firm.getLoginId()
        );

        if (!incomingHash.equals(firm.getPasswordHash())) {
            handleFailedLogin(out, firm);
            firmRepo.save(firm);
            return out;
        }

        // Correct password → reset counters
        firm.setFailedLoginAttempts(0);
        firm.setLockoutUntil(null);

        // Optional activation key
        if (StringUtils.hasText(req.activationKey)) {
            activateIfKeyMatches(firm, req.activationKey.trim());
        }

        // Ensure trial/expiry initialised if missing (old data)
        ensureTrial(firm);

        // Evaluate license
        evaluateLicense(firm, out);

        if (!out.licenseOk) {
            out.success = false;
            firmRepo.save(firm);
            log.info("LOGIN denied for loginId={}, licenseStatus={}",
                    firm.getLoginId(), out.licenseStatus);
            return out;
        }

        out.success = true;
        out.message = "Login successful.";

        firmRepo.save(firm);

        log.info("LOGIN success loginId={}, licenseLevel={}, expiry={}",
                firm.getLoginId(), out.licenseLevel, out.licenseExpiryAt);

        return out;
    }

    // ---------------- DEVELOPER RESET ----------------

    @Transactional
    public SimpleResult resetPasswordWithDeveloperKey(DeveloperResetRequest req) {
        SimpleResult out = new SimpleResult();

        Long firmId = (req.firmId != null ? req.firmId : 1L);
        FirmDetails firm = firmRepo.findById(firmId).orElse(null);
        if (firm == null) {
            out.success = false;
            out.message = "Firm not found.";
            return out;
        }

        if (!StringUtils.hasText(req.developerKey)) {
            out.success = false;
            out.message = "Developer key is required.";
            return out;
        }

        String hash = sha256(req.developerKey.trim());
        if (!DEV_RESET_KEY_HASH.equalsIgnoreCase(hash)) {
            out.success = false;
            out.message = "Invalid developer reset key.";
            return out;
        }

        if (!StringUtils.hasText(req.newPassword) || req.newPassword.length() < 6) {
            out.success = false;
            out.message = "New password must be at least 6 characters.";
            return out;
        }

        if (!StringUtils.hasText(firm.getLoginId())) {
            out.success = false;
            out.message = "Firm has no loginId configured yet.";
            return out;
        }

        firm.setPasswordHash(hashPassword(req.newPassword, firm.getLoginId()));
        firm.setFailedLoginAttempts(0);
        firm.setLockoutUntil(null);

        firmRepo.save(firm);

        log.info("Developer reset successful for firmId={}", firmId);

        out.success = true;
        out.message = "Password reset successful.";
        return out;
    }

    // ---------------- LICENSE / LOCKOUT HELPERS ----------------

    private void handleFailedLogin(LoginResult out, FirmDetails firm) {
        int attempts = (firm.getFailedLoginAttempts() == null ? 0 : firm.getFailedLoginAttempts());
        attempts++;
        firm.setFailedLoginAttempts(attempts);

        LocalDateTime now = LocalDateTime.now();

        if (attempts == FIRST_LOCK_THRESHOLD) {
            firm.setLockoutUntil(now.plusMinutes(5));
            out.locked = true;
            out.lockReason = "Too many failed attempts. Locked for 5 minutes.";
        } else if (attempts == SECOND_LOCK_THRESHOLD) {
            firm.setLockoutUntil(now.plusMinutes(30));
            out.locked = true;
            out.lockReason = "Too many failed attempts. Locked for 30 minutes.";
        } else if (attempts >= FINAL_LOCK_THRESHOLD) {
            firm.setLockoutUntil(now.plusYears(100));
            out.locked = true;
            out.lockReason = "Account locked. Please use 'Forgot password' / contact support.";
            out.showForgotPassword = true;
        }

        out.success = false;
        if (out.locked) {
            out.message = out.lockReason;
        } else {
            out.message = "Invalid login ID or password.";
        }

        log.warn("LOGIN failed for loginId={}, attempts={}, locked={}",
                firm.getLoginId(), attempts, out.locked);
    }

    private void ensureTrial(FirmDetails f) {
        if (f.getTrialStartDate() == null && !StringUtils.hasText(f.getLicenseLevel())) {
            // Completely fresh legacy row → start trial
            LocalDate today = LocalDate.now();
            Instant expiry = Instant.now().plus(Duration.ofDays(TRIAL_DAYS));
            f.setTrialStartDate(today);
            f.setLicenseLevel(LICENSE_TRIAL);
            f.setLicenseExpiryEncrypted(encryptExpiry(expiry));
            log.info("TRIAL initialised for firmId={}, expiry={}", f.getId(), expiry);
        }
        // If level exists but expiry corrupted/missing, we leave it as-is;
        // decryptExpiry() will fail → treated as expired.
    }

    private void evaluateLicense(FirmDetails f, LoginResult out) {
        Instant now = Instant.now();
        Instant expiry = decryptExpiry(f.getLicenseExpiryEncrypted());

        out.licenseExpiryAt = expiry;
        out.licenseLevel = f.getLicenseLevel();
        out.trial = LICENSE_TRIAL.equals(f.getLicenseLevel());

        if (expiry == null || now.isAfter(expiry)) {
            out.licenseOk = false;
            if (out.trial) {
                out.licenseStatus = "Trial expired";
            } else if (LICENSE_PREMIUM.equals(f.getLicenseLevel()) ||
                       LICENSE_PREMIUM_TEST.equals(f.getLicenseLevel())) {
                out.licenseStatus = "Premium license expired. Please enter a valid activation key.";
            } else {
                out.licenseStatus = "License expired.";
            }
        } else {
            out.licenseOk = true;
            if (out.trial) {
                out.licenseStatus = "Trial active";
            } else {
                out.licenseStatus = "Premium active";
            }
        }
    }

    private void activateIfKeyMatches(FirmDetails f, String raw) {
        if (!StringUtils.hasText(raw)) return;

        String h = sha256(raw);
        Instant now = Instant.now();
        Instant currentExpiry = decryptExpiry(f.getLicenseExpiryEncrypted());
        Instant base = (currentExpiry != null && currentExpiry.isAfter(now)) ? currentExpiry : now;

        if (h.equalsIgnoreCase(ACTIVATION_1Y_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM);
            f.setLicenseExpiryEncrypted(encryptExpiry(base.plus(Duration.ofDays(365))));
            log.info("Activation 1Y applied for firmId={}", f.getId());
        } else if (h.equalsIgnoreCase(ACTIVATION_3Y_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM);
            f.setLicenseExpiryEncrypted(encryptExpiry(base.plus(Duration.ofDays(365 * 3L))));
            log.info("Activation 3Y applied for firmId={}", f.getId());
        } else if (h.equalsIgnoreCase(ACTIVATION_LIFE_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM);
            f.setLicenseExpiryEncrypted(encryptExpiry(base.plus(Duration.ofDays(365 * 50L))));
            log.info("Activation LIFETIME applied for firmId={}", f.getId());
        } else if (h.equalsIgnoreCase(ACTIVATION_TEST_HASH)) {
            f.setLicenseLevel(LICENSE_PREMIUM_TEST);
            f.setLicenseExpiryEncrypted(encryptExpiry(now.plus(Duration.ofMinutes(2))));
            log.info("Activation TEST(2min) applied for firmId={}", f.getId());
        } else {
            log.warn("Invalid activation key attempted for firmId={}", f.getId());
        }
    }

    // ---------------- CRYPTO-LIKE UTILITIES (NO AES) ----------------

    private String hashPassword(String raw, String loginId) {
        String id = (loginId == null ? "" : loginId.toLowerCase());
        return sha256(PASSWORD_SALT + "|" + id + "|" + raw);
    }

    private String sha256(String x) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(x.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte bs : b) {
                sb.append(String.format("%02X", bs));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Store expiry as:   epochMillis:signature
     * where signature = sha256("LIC|" + epochMillis + "|" + LICENSE_SECRET_KEY)
     */
    private String encryptExpiry(Instant expiry) {
        if (expiry == null) return null;
        try {
            long ms = expiry.toEpochMilli();
            String ts = Long.toString(ms);
            String sig = sha256("LIC|" + ts + "|" + LICENSE_SECRET_KEY);
            String token = ts + ":" + sig;
            log.debug("encryptExpiry ms={}, token={}", ms, token);
            return token;
        } catch (Exception e) {
            log.error("ENCRYPT FAILED (signing expiry)", e);
            return null;
        }
    }

    /**
     * Decode epochMillis:signature and verify the signature.
     * On any problem → returns null → treated as expired / tampered.
     */
    private Instant decryptExpiry(String enc) {
        if (!StringUtils.hasText(enc)) {
            return null;
        }
        try {
            String[] parts = enc.split(":", 2);
            if (parts.length != 2) {
                log.warn("decryptExpiry invalid format token={}", enc);
                return null;
            }
            String ts = parts[0];
            String sig = parts[1];

            String expected = sha256("LIC|" + ts + "|" + LICENSE_SECRET_KEY);
            if (!expected.equalsIgnoreCase(sig)) {
                log.warn("decryptExpiry signature mismatch token={}", enc);
                return null;
            }

            long ms = Long.parseLong(ts.trim());
            Instant expiry = Instant.ofEpochMilli(ms);
            log.debug("decryptExpiry ok token={}, expiry={}", enc, expiry);
            return expiry;
        } catch (Exception e) {
            log.error("decryptExpiry FAILED → treating as expired", e);
            return null;
        }
    }
}
