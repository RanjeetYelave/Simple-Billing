package com.billing.simple.billsoft.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final String PASSWORD_SALT = "BILLSOFT-PWD-SALT-2025";
    private static final String DEV_RESET_KEY_HASH =
            "0CDB3704A8993071B98A9F4803DED2B39C54BE9DD0C688380896FB338AE1FACB";

    private static final String ACTIVATION_1Y_HASH =
            "9444E6D43B94AD97F7AFDC7D9EEC0FEE2D3B570D5505265D0058E52BE6C29795";
    private static final String ACTIVATION_3Y_HASH =
            "FD45AF8044174C891A2D776FD236D0B3ED402FFB5597CDF92875161B47677019";
    private static final String ACTIVATION_LIFE_HASH =
            "46C980C96774C909C3DCCD9647FCE69845BD5B1D8655C2F89FC785B1A34777C3";
    private static final String ACTIVATION_TEST_HASH =
            "6F1FAB01A6195F6B553B3CA078F0F171B96568CF3160B60EA28EBFCBECF535FE";

    private static final long TRIAL_DAYS = 30;
    private static final String LICENSE_TRIAL = "TRIAL";
    private static final String LICENSE_PREMIUM = "PREMIUM";
    private static final String LICENSE_PREMIUM_TEST = "PREMIUM_TEST";

    // 16 chars each
 // Must be EXACTLY 16 chars (128-bit)
    private static final String LICENSE_SECRET_KEY = "BILLSOFT-KEY-2025"; // 16 chars
    private static final String LICENSE_IV = "BILLSOFT-IV-2025";         // 16 chars

    private static final int FIRST_LOCK_THRESHOLD = 3;
    private static final int SECOND_LOCK_THRESHOLD = 6;
    private static final int FINAL_LOCK_THRESHOLD = 7;

    // ---------------- DTOs ----------------
    public static class LoginRequest { public String loginId; public String password; public String activationKey; }
    public static class RegisterRequest { public String loginId; public String password; }
    public static class DeveloperResetRequest { public Long firmId; public String developerKey; public String newPassword; }

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

    public static class SimpleResult {
        public boolean success;
        public String message;
    }

    // ---------------- REGISTER ----------------

    @Transactional
    public RegisterResult register(RegisterRequest req) {
        log.info("REGISTER called loginId={}", req.loginId);

        RegisterResult res = new RegisterResult();

        if (!StringUtils.hasText(req.loginId) || !StringUtils.hasText(req.password)) {
            res.success = false;
            res.message = "Login ID and password are required.";
            return res;
        }

        FirmDetails firm = firmRepo.findById(1L).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setId(1L);
            return firmRepo.save(f);
        });

        // Prevent duplicate loginId
        if (firm.getLoginId() != null &&
            firm.getLoginId().equalsIgnoreCase(req.loginId.trim())) {

            log.warn("Duplicate register attempt loginId={}", req.loginId);
            res.success = false;
            res.message = "Account already exists. Please login.";
            res.firmId = firm.getId();
            return res;
        }

        firm.setLoginId(req.loginId.trim());
        firm.setPasswordHash(hashPassword(req.password, firm.getLoginId()));

        // Init trial
        Instant expiry = Instant.now().plus(Duration.ofDays(TRIAL_DAYS));
        String encrypted = encryptExpiry(expiry);

        log.debug("TRIAL INIT: expiry={} encrypted={}", expiry, encrypted);

        firm.setTrialStartDate(LocalDate.now());
        firm.setLicenseLevel(LICENSE_TRIAL);
        firm.setLicenseExpiryEncrypted(encrypted);

        firmRepo.save(firm);
        log.info("REGISTER completed successfully, expiry saved={}", encrypted);

        res.success = true;
        res.message = "Account created successfully.";
        res.firmId = firm.getId();

        return res;
    }

    // ---------------- LOGIN ----------------

    @Transactional
    public LoginResult login(LoginRequest req) {
        log.info("LOGIN attempt loginId={}", req.loginId);
        LoginResult out = new LoginResult();

        FirmDetails firm = firmRepo.findById(1L).orElse(null);
        if (firm == null) {
            log.error("Firm missing!");
            out.success = false;
            out.message = "No account found.";
            return out;
        }

        log.debug("DB licenseEncrypted={}", firm.getLicenseExpiryEncrypted());

        // Login check
        if (!firm.getLoginId().equalsIgnoreCase(req.loginId)) {
            log.warn("Invalid loginId");
            out.success = false;
            out.message = "Invalid login.";
            return out;
        }

        if (!firm.getPasswordHash().equals(hashPassword(req.password, firm.getLoginId()))) {
            log.warn("Invalid password");
            handleFailedLogin(out, firm);
            firmRepo.save(firm);
            return out;
        }

        // Optional Activation
        if (StringUtils.hasText(req.activationKey)) {
            log.info("Applying activation key...");
            activateIfKeyMatches(firm, req.activationKey.trim());
        }

        firmRepo.save(firm);

        ensureTrial(firm);
        evaluateLicense(firm, out);

        log.debug("AFTER LICENSE: expiry={}, level={}, ok={}",
                out.licenseExpiryAt, out.licenseLevel, out.licenseOk);

        if (!out.licenseOk) {
            out.success = false;
            return out;
        }

        out.success = true;
        firmRepo.save(firm);
        log.info("LOGIN SUCCESS ✔");
        return out;
    }

    // ---------------- LICENSE / LOCKOUT HELPERS ----------------
    private void ensureTrial(FirmDetails f) {
        if (f.getLicenseExpiryEncrypted() == null) {
            Instant expiry = Instant.now().plus(Duration.ofDays(TRIAL_DAYS));
            String encrypted = encryptExpiry(expiry);
            log.warn("TRIAL FIX APPLIED expiry={} encrypted={}", expiry, encrypted);
            f.setLicenseLevel(LICENSE_TRIAL);
            f.setLicenseExpiryEncrypted(encrypted);
        }
    }

    private void evaluateLicense(FirmDetails f, LoginResult out) {
        Instant expiry = decryptExpiry(f.getLicenseExpiryEncrypted());
        Instant now = Instant.now();
        out.licenseExpiryAt = expiry;
        out.licenseLevel = f.getLicenseLevel();
        out.trial = LICENSE_TRIAL.equals(f.getLicenseLevel());

        if (expiry == null) {
            log.error("Expiry FAILED decrypt → treating license expired");
        }

        out.licenseOk = expiry != null && now.isBefore(expiry);
        out.licenseStatus = out.licenseOk ?
                (out.trial ? "Trial active" : "Premium active") :
                (out.trial ? "Trial expired" : "Premium expired");
    }

    private void activateIfKeyMatches(FirmDetails f, String raw) {
        String h = sha256(raw);
        log.debug("ACTIVATION: incomingHash={}", h);

        Instant now = Instant.now();
        Instant currentExpiry = decryptExpiry(f.getLicenseExpiryEncrypted());
        Instant base = (currentExpiry != null && currentExpiry.isAfter(now)) ? currentExpiry : now;

        if (h.equalsIgnoreCase(ACTIVATION_TEST_HASH)) {
            log.info("TEST key matched! +2min");
            f.setLicenseLevel(LICENSE_PREMIUM_TEST);
            f.setLicenseExpiryEncrypted(encryptExpiry(base.plus(Duration.ofMinutes(2))));
        }
    }

    // ---------------- CRYPTO ----------------
    private String encryptExpiry(Instant expiry) {
        if (expiry == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(LICENSE_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec iv = new IvParameterSpec(LICENSE_IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(String.valueOf(expiry.toEpochMilli()).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            log.error("ENCRYPT FAILED", e);
            return null;
        }
    }

    private Instant decryptExpiry(String enc) {
        if (!StringUtils.hasText(enc)) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(LICENSE_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec iv = new IvParameterSpec(LICENSE_IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            String s = new String(cipher.doFinal(Base64.getDecoder().decode(enc)),
                    StandardCharsets.UTF_8).trim();
            return Instant.ofEpochMilli(Long.parseLong(s));
        } catch (Exception e) {
            log.error("DECRYPT FAILED enc={}", enc, e);
            return null;
        }
    }

    // ---------------- OTHER HELPERS ----------------
    private void handleFailedLogin(LoginResult out, FirmDetails firm) {
        int n = (firm.getFailedLoginAttempts() == null ? 0 : firm.getFailedLoginAttempts()) + 1;
        firm.setFailedLoginAttempts(n);
    }

    private String hashPassword(String raw, String loginId) {
        return sha256(PASSWORD_SALT + "|" + loginId.toLowerCase() + "|" + raw);
    }

    private String sha256(String x) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(x.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte bs : b) sb.append(String.format("%02X", bs));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
