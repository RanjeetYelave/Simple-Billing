package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import com.billing.simple.billsoft.telegram.repo.*;
import com.billing.simple.billsoft.telegram.service.TelegramSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TelegramSecurityManagerTest {

    @Autowired
    private TelegramSecurityManager securityManager;

    @Autowired
    private TelegramAuthorizedUserRepository userRepo;

    @Autowired
    private TelegramLinkChallengeRepository challengeRepo;

    @Autowired
    private TelegramUserSessionRepository sessionRepo;

    @Autowired
    private TelegramDraftRepository draftRepo;

    @Autowired
    private TelegramDocumentDeliveryRepository deliveryRepo;

    @BeforeEach
    void setUp() {
        deliveryRepo.deleteAll();
        draftRepo.deleteAll();
        sessionRepo.deleteAll();
        challengeRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void testChallengeGenerationAndEntropy() {
        String code = securityManager.generateLinkChallenge();
        assertNotNull(code);
        assertEquals(9, code.length(), "Code format must be XXXX-XXXX (9 chars with hyphen)");
        assertTrue(code.contains("-"));

        // Generate multiple codes and ensure uniqueness
        String code2 = securityManager.generateLinkChallenge();
        assertNotEquals(code, code2);
    }

    @Test
    void testSuccessfulPairingRedemptionWithTargetUserId() {
        Long userId = 987654321L;
        String code = securityManager.generateLinkChallenge(userId);

        TelegramSecurityManager.PairingResult result = securityManager.attemptPairing(userId, "Raman Sharma", "ramansharma", code);
        assertTrue(result.isSuccess(), "Pairing must succeed with valid targeted code");
        assertFalse(result.isPendingApproval());
        assertNotNull(result.getUser());
        assertEquals(userId, result.getUser().getUserId());
        assertEquals("ACTIVE", result.getUser().getStatus());

        // Verify user is now recognized as authorized
        assertTrue(securityManager.isUserAuthorized(userId));

        // Replay attempt must be rejected (single-use)
        TelegramSecurityManager.PairingResult replay = securityManager.attemptPairing(userId, "Raman Sharma", "ramansharma", code);
        assertFalse(replay.isSuccess(), "Replaying a redeemed code must fail");
    }

    @Test
    void testTargetUserIdMismatchRejection() {
        Long targetUser = 987654321L;
        Long attackerUser = 111222333L;
        String code = securityManager.generateLinkChallenge(targetUser);

        TelegramSecurityManager.PairingResult result = securityManager.attemptPairing(attackerUser, "Attacker", "attacker", code);
        assertFalse(result.isSuccess(), "Pairing must fail when user ID does not match targetUser");
        assertFalse(result.isPendingApproval());
        assertTrue(result.getMessage().contains("was generated specifically for Telegram User ID " + targetUser));

        // Legitimate user can still redeem with correct user ID
        TelegramSecurityManager.PairingResult legitResult = securityManager.attemptPairing(targetUser, "Target Owner", "targetowner", code);
        assertTrue(legitResult.isSuccess(), "Targeted user must be able to redeem after mismatch attempt");
        assertEquals("ACTIVE", legitResult.getUser().getStatus());
    }

    @Test
    void testUntargetedChallengeRequiresDesktopApproval() {
        String code = securityManager.generateLinkChallenge(); // untargeted
        Long userId = 444555666L;

        TelegramSecurityManager.PairingResult result = securityManager.attemptPairing(userId, "Staff Member", "staff", code);
        assertFalse(result.isSuccess(), "Untargeted pairing should not be immediately active");
        assertTrue(result.isPendingApproval(), "Untargeted pairing must be in PENDING_APPROVAL state");
        assertNotNull(result.getUser());
        assertEquals("PENDING_APPROVAL", result.getUser().getStatus());

        // User must NOT be authorized yet
        assertFalse(securityManager.isUserAuthorized(userId));

        // Desktop owner approves user
        securityManager.approveUser(userId);

        // User must now be ACTIVE and authorized
        assertTrue(securityManager.isUserAuthorized(userId));
        assertEquals("ACTIVE", securityManager.getUser(userId).orElseThrow().getStatus());
    }

    @Test
    void testInvalidCodeAndRateLimiting() {
        Long userId = 123456789L;

        // 3 invalid attempts should trigger lockout
        for (int i = 0; i < 3; i++) {
            TelegramSecurityManager.PairingResult res = securityManager.attemptPairing(userId, "Bad User", "baduser", "INVALIDX");
            assertFalse(res.isSuccess());
        }

        // 4th attempt should be locked out
        TelegramSecurityManager.PairingResult locked = securityManager.attemptPairing(userId, "Bad User", "baduser", "ANYCODE1");
        assertFalse(locked.isSuccess());
        assertTrue(locked.getMessage().toLowerCase().contains("too many failed pairing attempts"));
    }

    @Test
    void testRevocationCascade() {
        Long userId = 555666777L;
        TelegramAuthorizedUser user = new TelegramAuthorizedUser(userId, "Operator Alice", "alice", "DESKTOP_OWNER");
        userRepo.save(user);
        assertTrue(securityManager.isUserAuthorized(userId));

        // Create active session
        TelegramUserSession session = new TelegramUserSession(1001L, userId);
        session.setActiveFirmId(1L);
        sessionRepo.save(session);

        // Create active draft
        TelegramDraft draft = new TelegramDraft();
        draft.setChatId(1001L);
        draft.setUserId(userId);
        draft.setFirmId(1L);
        draft.setIdempotencyKey("KEY-REVOKE-1");
        draft.setDraftState("BUILDING");
        draft.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        draftRepo.save(draft);

        // Create pending document delivery
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId("INST-1");
        delivery.setUserId(userId);
        delivery.setChatId(1001L);
        delivery.setFirmId(1L);
        delivery.setDocumentType("INVOICE_PDF");
        delivery.setDocumentId(1L);
        delivery.setDeliveryState("QUEUED");
        deliveryRepo.save(delivery);

        // Revoke user
        securityManager.revokeUser(userId);

        // 1. User must no longer be authorized
        assertFalse(securityManager.isUserAuthorized(userId));

        // 2. Session must be deleted
        assertTrue(sessionRepo.findByChatId(1001L).isEmpty());

        // 3. Draft must be cancelled
        TelegramDraft updatedDraft = draftRepo.findById(draft.getDraftId()).orElseThrow();
        assertEquals("CANCELLED", updatedDraft.getDraftState());

        // 4. Pending delivery must be cancelled
        TelegramDocumentDelivery updatedDelivery = deliveryRepo.findById(delivery.getDeliveryId()).orElseThrow();
        assertEquals("CANCELLED", updatedDelivery.getDeliveryState());
    }
}
