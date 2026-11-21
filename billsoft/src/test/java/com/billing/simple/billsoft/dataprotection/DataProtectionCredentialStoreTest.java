package com.billing.simple.billsoft.dataprotection;

import com.billing.simple.billsoft.licensing.LicensingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class DataProtectionCredentialStoreTest {

    @TempDir
    File tempDir;

    private File configFile;
    private PublicKey masterPublicKey;
    private PrivateKey masterPrivateKey;

    @BeforeEach
    public void setUp() throws Exception {
        configFile = new File(tempDir, "vault_config.json");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        masterPublicKey = kp.getPublic();
        masterPrivateKey = kp.getPrivate();
    }

    private String sign(int version, String protectedPayload) throws Exception {
        String canonical = version + "\n" + protectedPayload.trim();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(masterPrivateKey);
        sig.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    @Test
    public void testInitialVersionZeroFallback() {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);
        assertEquals(0, store.getCurrentVersion());
        String token = store.getCurrentToken();
        assertNull(token, "Unconfigured credential store should return null rather than hardcoded credentials");
    }

    @Test
    public void testDynamicPropertyResolutionAndIsolation() {
        String propKey = "rupeecrm.dataprotection.token";
        String licPropKey = "rupeecrm.licensing.token";
        try {
            System.setProperty(licPropKey, "license-secret-token");
            DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);
            assertNull(store.getCurrentToken(), "License token must not leak into Data Protection store");

            System.setProperty(propKey, "dp-secret-token");
            assertEquals("dp-secret-token", store.getCurrentToken());
        } finally {
            System.clearProperty(propKey);
            System.clearProperty(licPropKey);
        }
    }

    @Test
    public void testValidSignedCredentialUpdateAndPersistence() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

        String rawToken = "github_pat_ROTATED_TOKEN_1234567890_TEST";
        String maskedPayload = DataProtectionCredentialStore.maskPayload(rawToken);
        String signature = sign(1, maskedPayload);

        boolean updated = store.updateCredential(1, maskedPayload, signature, masterPublicKey);
        assertTrue(updated, "Valid signed credential update must succeed");
        assertEquals(1, store.getCurrentVersion());
        assertEquals(rawToken, store.getCurrentToken());
        assertTrue(configFile.exists(), "vault_config.json must be persisted to disk");

        // Reload store from disk
        DataProtectionCredentialStore reloadedStore = new DataProtectionCredentialStore(configFile);
        assertEquals(1, reloadedStore.getCurrentVersion());
        assertEquals(rawToken, reloadedStore.getCurrentToken());
    }

    @Test
    public void testInvalidSignatureRejectedAndExistingRetained() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);
        String initialToken = store.getCurrentToken();

        String rawToken = "github_pat_FORGED_TOKEN_999";
        String maskedPayload = DataProtectionCredentialStore.maskPayload(rawToken);
        String fakeSignature = Base64.getEncoder().encodeToString(new byte[64]); // Dummy invalid signature

        boolean updated = store.updateCredential(1, maskedPayload, fakeSignature, masterPublicKey);
        assertFalse(updated, "Invalid signature must be rejected");
        assertEquals(0, store.getCurrentVersion());
        assertEquals(initialToken, store.getCurrentToken(), "Existing credential must be preserved");
    }

    @Test
    public void testRollbackOrSameVersionRejected() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

        String tokenV2 = "github_pat_VERSION_2_TOKEN";
        String payloadV2 = DataProtectionCredentialStore.maskPayload(tokenV2);
        String sigV2 = sign(2, payloadV2);

        assertTrue(store.updateCredential(2, payloadV2, sigV2, masterPublicKey));
        assertEquals(2, store.getCurrentVersion());
        assertEquals(tokenV2, store.getCurrentToken());

        // Attempt rollback to Version 1
        String tokenV1 = "github_pat_VERSION_1_TOKEN";
        String payloadV1 = DataProtectionCredentialStore.maskPayload(tokenV1);
        String sigV1 = sign(1, payloadV1);

        assertFalse(store.updateCredential(1, payloadV1, sigV1, masterPublicKey), "Rollback to older version must be rejected");
        assertEquals(2, store.getCurrentVersion());
        assertEquals(tokenV2, store.getCurrentToken());

        // Attempt duplicate same version (Version 2)
        assertFalse(store.updateCredential(2, payloadV2, sigV2, masterPublicKey), "Same version must be ignored");
        assertEquals(2, store.getCurrentVersion());
    }

    @Test
    public void testMaskAndUnmaskRoundtrip() {
        String testToken = "github_pat_DUMMY_DP_TEST_TOKEN_XYZ1234567890";
        String masked = DataProtectionCredentialStore.maskPayload(testToken);
        assertNotNull(masked);
        assertNotEquals(testToken, masked);
        String unmasked = DataProtectionCredentialStore.unmaskPayload(masked);
        assertEquals(testToken, unmasked);
    }

    @Test
    public void testFallbackKeyVerificationSucceeds() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair fallbackKp = kpg.generateKeyPair();
        PublicKey fallbackPubKey = fallbackKp.getPublic();
        PrivateKey fallbackPrivKey = fallbackKp.getPrivate();

        String rawToken = "github_pat_FALLBACK_KEY_TOKEN_12345";
        String maskedPayload = DataProtectionCredentialStore.maskPayload(rawToken);

        // Sign with fallback private key (not masterPrivateKey)
        String canonical = 1 + "\n" + maskedPayload.trim();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(fallbackPrivKey);
        sig.update(canonical.getBytes(StandardCharsets.UTF_8));
        String fallbackSig = Base64.getEncoder().encodeToString(sig.sign());

        // Update passing primary and fallback keys
        boolean updated = store.updateCredential(1, maskedPayload, fallbackSig, masterPublicKey, fallbackPubKey);
        assertTrue(updated, "Update signed by fallback key must succeed when fallback key is provided");
        assertEquals(1, store.getCurrentVersion());
        assertEquals(rawToken, store.getCurrentToken());
    }

    @Test
    public void testBothPrimaryAndFallbackMismatchRejected() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair otherKp = kpg.generateKeyPair();
        KeyPair fallbackKp = kpg.generateKeyPair();

        String rawToken = "github_pat_UNKNOWN_KEY_TOKEN";
        String maskedPayload = DataProtectionCredentialStore.maskPayload(rawToken);

        // Sign with an unrelated private key
        String canonical = 1 + "\n" + maskedPayload.trim();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(otherKp.getPrivate());
        sig.update(canonical.getBytes(StandardCharsets.UTF_8));
        String invalidSig = Base64.getEncoder().encodeToString(sig.sign());

        boolean updated = store.updateCredential(1, maskedPayload, invalidSig, masterPublicKey, fallbackKp.getPublic());
        assertFalse(updated, "Update signed by unknown key must be rejected");
        assertEquals(0, store.getCurrentVersion());
    }

    @Test
    public void testCorruptOrEmptyPayloadRejected() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);
        assertFalse(store.updateCredential(1, "", "sig", masterPublicKey));
        assertFalse(store.updateCredential(1, null, "sig", masterPublicKey));
        assertFalse(store.updateCredential(1, "payload", null, masterPublicKey));
        assertFalse(store.updateCredential(1, "payload", "sig", null));
    }
}
