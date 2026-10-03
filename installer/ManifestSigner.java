package com.billing.simple.installer;

import java.io.File;
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
            System.out.println("  sign   <packageZipPath> <version> <privateKeyBase64> <outputDir>");
            System.out.println("  verify <manifestJsonPath> <signaturePath> <publicKeyBase64>");
            System.exit(1);
        }

        String command = args[0];
        if ("sign".equalsIgnoreCase(command)) {
            Path packageZip = Paths.get(args[1]);
            String version = args[2];
            String privateKeyBase64 = args[3];
            Path outputDir = Paths.get(args[4]);

            generateAndSignManifest(packageZip, version, privateKeyBase64, outputDir);
        } else if ("verify".equalsIgnoreCase(command)) {
            Path manifestJson = Paths.get(args[1]);
            Path signaturePath = Paths.get(args[2]);
            String publicKeyBase64 = args[3];

            boolean valid = verifyManifest(manifestJson, signaturePath, publicKeyBase64);
            System.out.println("Manifest verification result: " + (valid ? "VALID" : "INVALID"));
            if (!valid) System.exit(2);
        }
    }

    public static void generateAndSignManifest(Path packageZip, String version, String privateKeyBase64, Path outputDir) throws Exception {
        byte[] packageBytes = Files.readAllBytes(packageZip);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(packageBytes);
        StringBuilder hashHex = new StringBuilder();
        for (byte b : hash) {
            hashHex.append(String.format("%02x", b));
        }

        String filename = packageZip.getFileName().toString();
        long sizeBytes = Files.size(packageZip);
        String isoTime = Instant.now().toString();

        String manifestJson = "{\n" +
                "  \"version\": \"" + version + "\",\n" +
                "  \"releaseTag\": \"v" + version + "\",\n" +
                "  \"publishedAt\": \"" + isoTime + "\",\n" +
                "  \"minInstallerVersion\": \"1.0.0\",\n" +
                "  \"package\": {\n" +
                "    \"filename\": \"" + filename + "\",\n" +
                "    \"sha256\": \"" + hashHex + "\",\n" +
                "    \"sizeBytes\": " + sizeBytes + "\n" +
                "  }\n" +
                "}\n";

        Files.createDirectories(outputDir);
        Path manifestPath = outputDir.resolve("manifest.json");
        Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

        // Sign with Ed25519
        byte[] privBytes = Base64.getDecoder().decode(privateKeyBase64.trim());
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privBytes);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PrivateKey privKey = kf.generatePrivate(keySpec);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privKey);
        sig.update(manifestJson.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signature);

        Path sigPath = outputDir.resolve("manifest.sig");
        Files.writeString(sigPath, signatureBase64, StandardCharsets.UTF_8);

        System.out.println("Generated signed release manifest at " + manifestPath);
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
