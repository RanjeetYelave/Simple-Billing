package com.billing.simple.installer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Native Ed25519 Release Manifest Generator & Signature Validator for RupeeCRM.
 * Runs on standard JDK 21 without external dependencies.
 */
public class ManifestSigner {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java ManifestSigner [sign|verify] <args>");
            System.out.println("  sign   <warPath> <version> <privateKeyBase64> <outputDir> [releaseNotes]");
            System.out.println("  verify <manifestJsonPath> <signaturePath> <publicKeyBase64>");
            System.exit(1);
        }

        String command = args[0];
        if ("sign".equalsIgnoreCase(command)) {
            if (args.length < 5) {
                System.err.println("Error: sign requires <warPath> <version> <privateKeyBase64> <outputDir> [releaseNotes]");
                System.exit(1);
            }
            Path warPath = Paths.get(args[1]);
            String version = args[2].replaceAll("^[vV]", "").trim();
            String privateKeyBase64 = args[3];
            Path outputDir = Paths.get(args[4]);
            String releaseNotes = args.length > 5 ? args[5] : "Release notes are not available for this version.";

            generateAndSignManifest(warPath, version, privateKeyBase64, outputDir, releaseNotes);
        } else if ("verify".equalsIgnoreCase(command)) {
            if (args.length < 4) {
                System.err.println("Error: verify requires <manifestJsonPath> <signaturePath> <publicKeyBase64>");
                System.exit(1);
            }
            Path manifestJson = Paths.get(args[1]);
            Path signaturePath = Paths.get(args[2]);
            String publicKeyBase64 = args[3];

            boolean valid = verifyManifest(manifestJson, signaturePath, publicKeyBase64);
            System.out.println("Manifest verification result: " + (valid ? "VALID" : "INVALID"));
            if (!valid) System.exit(2);
        }
    }

    public static void generateAndSignManifest(Path warPath, String version, String privateKeyBase64, Path outputDir, String releaseNotes) throws Exception {
        byte[] warBytes = Files.readAllBytes(warPath);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(warBytes);
        StringBuilder hashHex = new StringBuilder();
        for (byte b : hash) {
            hashHex.append(String.format("%02x", b));
        }

        String filename = warPath.getFileName().toString();
        long sizeBytes = Files.size(warPath);
        String isoTime = Instant.now().toString();
        String downloadUrl = "https://github.com/RanjeetYelave/Simple-Billing/releases/download/v" + version + "/" + filename;

        String safeNotes = releaseNotes != null && !releaseNotes.trim().isEmpty()
                ? releaseNotes.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                : "Release notes are not available for this version.";

        String manifestJson = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"appVersion\": \"" + version + "\",\n" +
                "  \"releaseTag\": \"v" + version + "\",\n" +
                "  \"publishedAt\": \"" + isoTime + "\",\n" +
                "  \"minLauncherVersion\": \"1.0.0\",\n" +
                "  \"artifact\": {\n" +
                "    \"filename\": \"" + filename + "\",\n" +
                "    \"url\": \"" + downloadUrl + "\",\n" +
                "    \"sha256\": \"" + hashHex + "\",\n" +
                "    \"sizeBytes\": " + sizeBytes + "\n" +
                "  },\n" +
                "  \"releaseNotes\": \"" + safeNotes + "\"\n" +
                "}\n";

        Files.createDirectories(outputDir);
        Path manifestPath = outputDir.resolve("manifest.json");
        Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

        // Sign exact UTF-8 bytes with Ed25519
        PrivateKey privKey;
        if ("DEV_EPHEMERAL_KEY".equalsIgnoreCase(privateKeyBase64 != null ? privateKeyBase64.trim() : "")) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();
            privKey = kp.getPrivate();
            System.err.println("WARNING: Manifest signed with ephemeral development test key! Public key: " + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
        } else if (privateKeyBase64 == null || privateKeyBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("CRITICAL: privateKeyBase64 must not be null or empty. Production keys must be explicitly provided.");
        } else {
            byte[] privBytes = Base64.getDecoder().decode(privateKeyBase64.trim());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privBytes);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            privKey = kf.generatePrivate(keySpec);
        }

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privKey);
        sig.update(manifestJson.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signature);

        Path sigPath = outputDir.resolve("manifest.sig");
        Files.writeString(sigPath, signatureBase64, StandardCharsets.UTF_8);

        System.out.println("Generated signed release manifest at " + manifestPath);
        System.out.println("Generated detached signature at " + sigPath);
    }

    public static boolean verifyManifest(Path manifestJson, Path signaturePath, String publicKeyBase64) {
        try {
            byte[] manifestBytes = Files.readAllBytes(manifestJson);
            String sigBase64 = Files.readString(signaturePath, StandardCharsets.UTF_8).trim();
            byte[] sigBytes = Base64.getDecoder().decode(sigBase64);

            byte[] pubBytes = Base64.getDecoder().decode(publicKeyBase64.trim());
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubBytes);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PublicKey pubKey = kf.generatePublic(keySpec);

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pubKey);
            sig.update(manifestBytes);
            return sig.verify(sigBytes);
        } catch (Exception e) {
            System.err.println("Verification failed with exception: " + e.getMessage());
            return false;
        }
    }
}
