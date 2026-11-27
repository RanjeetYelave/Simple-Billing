package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import com.billing.simple.billsoft.telegram.entities.TelegramLinkChallenge;
import com.billing.simple.billsoft.telegram.repo.TelegramAuthorizedUserRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramLinkChallengeRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramSecurityManager {

    private static final Logger log = LoggerFactory.getLogger(TelegramSecurityManager.class);
    private static final String CROCKFORD_BASE32 = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramAuthorizedUserRepository userRepo;
    private final TelegramLinkChallengeRepository challengeRepo;
    private final TelegramUserSessionRepository sessionRepo;
    private final TelegramDraftRepository draftRepo;
    private final TelegramDocumentDeliveryRepository deliveryRepo;

    // In-memory rate limiting for failed pairing attempts: userId -> [timestamp, count]
    private final Map<Long, UserAttemptTracker> userAttemptTrackers = new ConcurrentHashMap<>();

    public TelegramSecurityManager(TelegramAuthorizedUserRepository userRepo,
                                   TelegramLinkChallengeRepository challengeRepo,
                                   TelegramUserSessionRepository sessionRepo,
                                   TelegramDraftRepository draftRepo,
                                   TelegramDocumentDeliveryRepository deliveryRepo) {
        this.userRepo = userRepo;
        this.challengeRepo = challengeRepo;
        this.sessionRepo = sessionRepo;
        this.draftRepo = draftRepo;
        this.deliveryRepo = deliveryRepo;
    }

    public boolean isUserAuthorized(Long userId) {
        if (userId == null) return false;
        return userRepo.findByUserIdAndStatus(userId, "ACTIVE").isPresent();
    }

    public void updateLastActive(Long userId) {
        if (userId == null) return;
        userRepo.findById(userId).ifPresent(u -> {
            u.setLastActiveAt(LocalDateTime.now());
            userRepo.save(u);
        });
    }

    @Transactional
    public String generateLinkChallenge() {
        return generateLinkChallenge(null);
    }

    @Transactional
    public String generateLinkChallenge(Long targetUserId) {
        // Expire older pending challenges
        challengeRepo.expirePendingChallenges(LocalDateTime.now());

        // Generate 8 characters from Crockford Base32
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            int idx = RANDOM.nextInt(CROCKFORD_BASE32.length());
            sb.append(CROCKFORD_BASE32.charAt(idx));
        }
        String rawCode = sb.toString();
        String formattedCode = rawCode.substring(0, 4) + "-" + rawCode.substring(4, 8);
        String hash = computeSha256(rawCode);

        TelegramLinkChallenge challenge = new TelegramLinkChallenge();
        challenge.setChallengeHash(hash);
        challenge.setTargetUserId(targetUserId);
        challenge.setStatus("PENDING");
        challenge.setFailedAttempts(0);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        challenge.setCreatedAt(LocalDateTime.now());
        challengeRepo.save(challenge);

        return formattedCode;
    }

    @Transactional
    public PairingResult attemptPairing(Long userId, String displayName, String username, String inputCode) {
        if (userId == null || inputCode == null) {
            return PairingResult.failed("Invalid input");
        }

        // Check rate limiting (max 3 failed attempts in 15 minutes per user)
        UserAttemptTracker tracker = userAttemptTrackers.computeIfAbsent(userId, k -> new UserAttemptTracker());
        if (tracker.isLockedOut()) {
            return PairingResult.failed("Too many failed pairing attempts. Please wait 15 minutes.");
        }

        String normalized = inputCode.trim().toUpperCase().replace("-", "").replace(" ", "");
        if (normalized.length() != 8) {
            tracker.recordFailedAttempt();
            return PairingResult.failed("Invalid pairing code format. Expected 8 characters.");
        }

        String hash = computeSha256(normalized);
        Optional<TelegramLinkChallenge> challengeOpt = challengeRepo.findByChallengeHashAndStatus(hash, "PENDING");

        if (challengeOpt.isEmpty()) {
            tracker.recordFailedAttempt();
            return PairingResult.failed("Pairing code invalid or expired.");
        }

        TelegramLinkChallenge challenge = challengeOpt.get();
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            challenge.setStatus("EXPIRED");
            challengeRepo.save(challenge);
            tracker.recordFailedAttempt();
            return PairingResult.failed("Pairing code has expired. Request a new code from desktop.");
        }

        if (challenge.getFailedAttempts() >= 5) {
            challenge.setStatus("EXPIRED");
            challengeRepo.save(challenge);
            tracker.recordFailedAttempt();
            return PairingResult.failed("Too many failed attempts on this code. Request a new code.");
        }

        // Validate target user if challenge was locked to a specific Telegram User ID
        if (challenge.getTargetUserId() != null && !challenge.getTargetUserId().equals(userId)) {
            tracker.recordFailedAttempt();
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            challengeRepo.save(challenge);
            log.warn("Telegram pairing target mismatch: challenge expected user {}, got user {}", challenge.getTargetUserId(), userId);
            return PairingResult.failed("This pairing code was generated specifically for Telegram User ID "
                    + challenge.getTargetUserId() + ", but your Telegram User ID is " + userId + ".");
        }

        // Atomic redemption
        int redeemed = challengeRepo.redeemChallenge(hash, userId, LocalDateTime.now());
        if (redeemed == 0) {
            tracker.recordFailedAttempt();
            return PairingResult.failed("Code was already used or expired.");
        }

        // Register or reactivate user
        Optional<TelegramAuthorizedUser> existing = userRepo.findById(userId);
        TelegramAuthorizedUser user = existing.orElse(new TelegramAuthorizedUser());
        user.setUserId(userId);
        user.setDisplayName(displayName != null ? displayName : "Telegram User " + userId);
        user.setUsername(username);
        user.setAuthorizedBy("DESKTOP_OWNER");
        user.setAuthorizedAt(LocalDateTime.now());
        user.setLastActiveAt(LocalDateTime.now());

        // Targeted challenges are directly ACTIVE; open/untargeted challenges require desktop owner approval
        boolean isDirectApproval = (challenge.getTargetUserId() != null);
        if (isDirectApproval) {
            user.setStatus("ACTIVE");
            userRepo.save(user);
            tracker.reset();
            return PairingResult.success(user);
        } else {
            user.setStatus("PENDING_APPROVAL");
            userRepo.save(user);
            tracker.reset();
            return PairingResult.pendingApproval(user);
        }
    }

    public Optional<TelegramAuthorizedUser> getUser(Long userId) {
        if (userId == null) return Optional.empty();
        return userRepo.findById(userId);
    }

    @Transactional
    public void approveUser(Long userId) {
        if (userId == null) return;
        userRepo.updateStatus(userId, "ACTIVE");
        log.info("Approved Telegram user {}", userId);
    }

    @Transactional
    public void revokeUser(Long userId) {
        if (userId == null) return;
        userRepo.updateStatus(userId, "REVOKED");
        sessionRepo.deleteByUserId(userId);
        draftRepo.cancelActiveDraftsForUser(userId);
        deliveryRepo.cancelPendingDeliveriesForUser(userId);
        log.info("Revoked Telegram user {} and cascaded cleanup across sessions, drafts, and deliveries.", userId);
    }

    public List<TelegramAuthorizedUser> listAuthorizedUsers() {
        return userRepo.findAllByOrderByAuthorizedAtDesc();
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static class PairingResult {
        private final boolean success;
        private final boolean pendingApproval;
        private final String message;
        private final TelegramAuthorizedUser user;

        private PairingResult(boolean success, boolean pendingApproval, String message, TelegramAuthorizedUser user) {
            this.success = success;
            this.pendingApproval = pendingApproval;
            this.message = message;
            this.user = user;
        }

        public static PairingResult success(TelegramAuthorizedUser user) {
            return new PairingResult(true, false, "Paired successfully", user);
        }

        public static PairingResult pendingApproval(TelegramAuthorizedUser user) {
            return new PairingResult(false, true, "Pairing code accepted. Pending desktop owner approval.", user);
        }

        public static PairingResult failed(String message) {
            return new PairingResult(false, false, message, null);
        }

        public boolean isSuccess() { return success; }
        public boolean isPendingApproval() { return pendingApproval; }
        public String getMessage() { return message; }
        public TelegramAuthorizedUser getUser() { return user; }
    }

    private static class UserAttemptTracker {
        private int failedCount = 0;
        private LocalDateTime lockoutUntil = null;

        public synchronized boolean isLockedOut() {
            if (lockoutUntil != null) {
                if (lockoutUntil.isAfter(LocalDateTime.now())) {
                    return true;
                } else {
                    lockoutUntil = null;
                    failedCount = 0;
                }
            }
            return false;
        }

        public synchronized void recordFailedAttempt() {
            failedCount++;
            if (failedCount >= 3) {
                lockoutUntil = LocalDateTime.now().plusMinutes(15);
            }
        }

        public synchronized void reset() {
            failedCount = 0;
            lockoutUntil = null;
        }
    }
}
