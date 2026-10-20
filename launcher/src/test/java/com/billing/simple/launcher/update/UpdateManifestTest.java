package com.billing.simple.launcher.update;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

class UpdateManifestTest {

    @Test
    void testValidManifestRoundtrip() {
        UpdateManifest manifest = new UpdateManifest(
                1,
                "1.0.12",
                "v1.0.12",
                "2026-10-19T14:30:00Z",
                "1.0.0",
                "rupeecrm.war",
                "https://github.com/RanjeetYelave/Simple-Billing/releases/download/v1.0.12/rupeecrm.war",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                52_000_000L,
                "• Added Smart Reorder\n• Fixed thermal print alignment"
        );

        String json = manifest.toJson();
        assertNotNull(json);

        UpdateManifest parsed = UpdateManifest.parse(json);
        assertEquals(1, parsed.getSchemaVersion());
        assertEquals("1.0.12", parsed.getAppVersion());
        assertEquals("v1.0.12", parsed.getReleaseTag());
        assertEquals("rupeecrm.war", parsed.getArtifactFilename());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", parsed.getArtifactSha256());
        assertEquals(52_000_000L, parsed.getArtifactSizeBytes());
        assertTrue(parsed.getReleaseNotes().contains("Smart Reorder"));
    }

    @Test
    void testParseAndVerifyValidSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        UpdateManifest manifest = new UpdateManifest(
                1, "1.0.15", "v1.0.15", "2026-10-19T15:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Fixes"
        );

        byte[] jsonBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(jsonBytes, kp.getPrivate());

        UpdateManifest verified = UpdateManifest.parseAndVerify(jsonBytes, signature, kp.getPublic());
        assertNotNull(verified);
        assertEquals("1.0.15", verified.getAppVersion());
    }

    @Test
    void testParseAndVerifyTamperedFails() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        UpdateManifest manifest = new UpdateManifest(
                1, "1.0.15", "v1.0.15", "2026-10-19T15:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Fixes"
        );

        byte[] jsonBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(jsonBytes, kp.getPrivate());

        // Tamper with payload
        byte[] tamperedBytes = manifest.toJson().replace("1.0.15", "1.0.16").getBytes(StandardCharsets.UTF_8);

        assertThrows(GeneralSecurityException.class, () ->
                UpdateManifest.parseAndVerify(tamperedBytes, signature, kp.getPublic())
        );
    }

    @Test
    void testRejectsPathTraversalFilename() {
        assertThrows(SecurityException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "../../etc/passwd.war", "https://example.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        20_000_000L, "Notes"
                )
        );

        assertThrows(SecurityException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "sub/path.war", "https://example.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        20_000_000L, "Notes"
                )
        );
    }

    @Test
    void testRejectsNonWarExtension() {
        assertThrows(IllegalArgumentException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "malicious.exe", "https://example.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        20_000_000L, "Notes"
                )
        );
    }

    @Test
    void testRejectsMalformedSha256() {
        assertThrows(IllegalArgumentException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "rupeecrm.war", "https://example.com/rupeecrm.war",
                        "not-a-valid-sha256",
                        20_000_000L, "Notes"
                )
        );
    }

    @Test
    void testRejectsOutOfBoundsSize() {
        // Less than 10KB
        assertThrows(IllegalArgumentException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "rupeecrm.war", "https://example.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        500L, "Notes"
                )
        );

        // Greater than 250MB
        assertThrows(IllegalArgumentException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "rupeecrm.war", "https://example.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        500_000_000L, "Notes"
                )
        );
    }

    @Test
    void testFallbackForMissingReleaseNotes() {
        UpdateManifest manifest = new UpdateManifest(
                1, "1.0.0", "v1.0.0", "now", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                20_000_000L, null
        );
        assertEquals(UpdateManifest.DEFAULT_NO_NOTES, manifest.getReleaseNotes());

        UpdateManifest blankNotes = new UpdateManifest(
                1, "1.0.0", "v1.0.0", "now", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                20_000_000L, "   "
        );
        assertEquals(UpdateManifest.DEFAULT_NO_NOTES, blankNotes.getReleaseNotes());
    }

    @Test
    void testRejectsInsecureOrArbitraryUrlSchemes() {
        // file: scheme rejected
        assertThrows(SecurityException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "rupeecrm.war", "file:///etc/shadow",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        20_000_000L, "Notes"
                )
        );

        // ftp: scheme rejected
        assertThrows(SecurityException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "rupeecrm.war", "ftp://ftp.example.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        20_000_000L, "Notes"
                )
        );

        // remote plaintext http: rejected
        assertThrows(SecurityException.class, () ->
                new UpdateManifest(
                        1, "1.0.0", "v1.0.0", "now", "1.0.0",
                        "rupeecrm.war", "http://insecure-remote.com/rupeecrm.war",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        20_000_000L, "Notes"
                )
        );

        // loopback http: allowed for local testing
        UpdateManifest loopback = new UpdateManifest(
                1, "1.0.0", "v1.0.0", "now", "1.0.0",
                "rupeecrm.war", "http://127.0.0.1:8080/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                20_000_000L, "Notes"
        );
        assertEquals("http://127.0.0.1:8080/rupeecrm.war", loopback.getArtifactUrl());
    }

    @Test
    void testParseStripsLeadingUtf8Bom() {
        String jsonWithBom = "\uFEFF{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"appVersion\": \"1.0.20\",\n" +
                "  \"releaseTag\": \"v1.0.20\",\n" +
                "  \"publishedAt\": \"2026-10-20T10:00:00Z\",\n" +
                "  \"minLauncherVersion\": \"1.0.0\",\n" +
                "  \"artifact\": {\n" +
                "    \"filename\": \"rupeecrm.war\",\n" +
                "    \"url\": \"https://example.com/rupeecrm.war\",\n" +
                "    \"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\n" +
                "    \"sizeBytes\": 20000000\n" +
                "  },\n" +
                "  \"releaseNotes\": \"Notes with BOM\"\n" +
                "}";

        UpdateManifest parsed = UpdateManifest.parse(jsonWithBom);
        assertNotNull(parsed);
        assertEquals("1.0.20", parsed.getAppVersion());
        assertEquals("Notes with BOM", parsed.getReleaseNotes());
    }

    @Test
    void testParseAndVerifySignedWithBomSucceeds() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        String rawJson = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"appVersion\": \"1.0.21\",\n" +
                "  \"releaseTag\": \"v1.0.21\",\n" +
                "  \"publishedAt\": \"2026-10-20T10:00:00Z\",\n" +
                "  \"minLauncherVersion\": \"1.0.0\",\n" +
                "  \"artifact\": {\n" +
                "    \"filename\": \"rupeecrm.war\",\n" +
                "    \"url\": \"https://example.com/rupeecrm.war\",\n" +
                "    \"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\n" +
                "    \"sizeBytes\": 20000000\n" +
                "  },\n" +
                "  \"releaseNotes\": \"Signed with BOM\"\n" +
                "}";
        byte[] bytesWithBom = ("\uFEFF" + rawJson).getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(bytesWithBom, kp.getPrivate());

        UpdateManifest verified = UpdateManifest.parseAndVerify(bytesWithBom, signature, kp.getPublic());
        assertNotNull(verified);
        assertEquals("1.0.21", verified.getAppVersion());
        assertEquals("Signed with BOM", verified.getReleaseNotes());
    }

    @Test
    void testParseAndVerifySignedWithoutBomRejectsPrependBom() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        String rawJson = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"appVersion\": \"1.0.22\",\n" +
                "  \"releaseTag\": \"v1.0.22\",\n" +
                "  \"publishedAt\": \"2026-10-20T10:00:00Z\",\n" +
                "  \"minLauncherVersion\": \"1.0.0\",\n" +
                "  \"artifact\": {\n" +
                "    \"filename\": \"rupeecrm.war\",\n" +
                "    \"url\": \"https://example.com/rupeecrm.war\",\n" +
                "    \"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\n" +
                "    \"sizeBytes\": 20000000\n" +
                "  },\n" +
                "  \"releaseNotes\": \"Original bytes\"\n" +
                "}";
        byte[] originalBytes = rawJson.getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(originalBytes, kp.getPrivate());

        // Attacker prepends UTF-8 BOM bytes [0xEF, 0xBB, 0xBF]
        byte[] tamperedWithBom = ("\uFEFF" + rawJson).getBytes(StandardCharsets.UTF_8);

        // Verification MUST FAIL because the signed byte stream was altered
        assertThrows(GeneralSecurityException.class, () ->
                UpdateManifest.parseAndVerify(tamperedWithBom, signature, kp.getPublic())
        );
    }

    @Test
    void testParseAndVerifyRejectsNewlineNormalizationTampering() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        String unixJson = "{\n  \"schemaVersion\": 1,\n  \"appVersion\": \"1.0.23\",\n  \"releaseTag\": \"v1.0.23\",\n  \"publishedAt\": \"2026-10-20T10:00:00Z\",\n  \"minLauncherVersion\": \"1.0.0\",\n  \"artifact\": {\n    \"filename\": \"rupeecrm.war\",\n    \"url\": \"https://example.com/rupeecrm.war\",\n    \"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\n    \"sizeBytes\": 20000000\n  },\n  \"releaseNotes\": \"Unix newlines\"\n}\n";
        byte[] unixBytes = unixJson.getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(unixBytes, kp.getPrivate());

        // Alter newlines to Windows CRLF
        String crlfJson = unixJson.replace("\n", "\r\n");
        byte[] crlfBytes = crlfJson.getBytes(StandardCharsets.UTF_8);

        // Verification MUST FAIL because Ed25519 signs exact bytes, no silent normalization
        assertThrows(GeneralSecurityException.class, () ->
                UpdateManifest.parseAndVerify(crlfBytes, signature, kp.getPublic())
        );
    }

    @Test
    void testParseAndVerifyRejectsAlteredArtifactSha256() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        UpdateManifest original = new UpdateManifest(
                1, "1.0.24", "v1.0.24", "2026-10-20T10:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                20000000, "Notes"
        );
        byte[] originalBytes = original.toJson().getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(originalBytes, kp.getPrivate());

        // Attacker modifies only the hash field
        String tamperedJson = original.toJson().replace("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        byte[] tamperedBytes = tamperedJson.getBytes(StandardCharsets.UTF_8);

        assertThrows(GeneralSecurityException.class, () ->
                UpdateManifest.parseAndVerify(tamperedBytes, signature, kp.getPublic())
        );
    }

    @Test
    void testParseAndVerifyRejectsMalformedSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        UpdateManifest original = new UpdateManifest(
                1, "1.0.25", "v1.0.25", "2026-10-20T10:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                20000000, "Notes"
        );
        byte[] originalBytes = original.toJson().getBytes(StandardCharsets.UTF_8);

        // Malformed Base64
        assertThrows(GeneralSecurityException.class, () ->
                UpdateManifest.parseAndVerify(originalBytes, "NOT_VALID_BASE64!@#$%", kp.getPublic())
        );

        // Truncated signature
        assertThrows(GeneralSecurityException.class, () ->
                UpdateManifest.parseAndVerify(originalBytes, "dGVzdA==", kp.getPublic())
        );
    }
}
