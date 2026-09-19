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
        assertNotNull(token);
        assertTrue(token.startsWith("github_pat_"));
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
    public void testCorruptOrEmptyPayloadRejected() throws Exception {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore(configFile);
        assertFalse(store.updateCredential(1, "", "sig", masterPublicKey));
        assertFalse(store.updateCredential(1, null, "sig", masterPublicKey));
        assertFalse(store.updateCredential(1, "payload", null, masterPublicKey));
        assertFalse(store.updateCredential(1, "payload", "sig", null));
    }
}
