package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class CrossImplementationSchema3VerifierTest {

    private KeyPair keyPair;
    private LicenseVerifier verifier;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();
        this.verifier = new LicenseVerifier(keyPair.getPublic());
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private String signData(String canonicalData) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonicalData.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    @Test
    void testManagementGeneratedSchema3EmiLicenseVerification() throws Exception {
        String licenseId = "LIC-101";
        String machineId = "TEST-MID-2026-AAAA";
        String customerName = "ABC Electronics";
        String product = "RupeeCRM";
        String edition = "PROFESSIONAL";
        String plan = "GOLD";
        String status = "ACTIVE";
        int revision = 1;
        String issuedAt = "2026-09-23T10:00:00Z";
        String expiresAt = "2027-09-23T10:00:00Z";
        boolean dpEnabled = true;
        String dpExpiresAt = "2027-09-23T10:00:00Z";
        String statusReason = "Initial Issuance";

        StringBuilder canonical = new StringBuilder();
        canonical.append("3\n")
                .append(licenseId).append("\n")
                .append(machineId).append("\n")
                .append(customerName).append("\n")
                .append(product).append("\n")
                .append(edition).append("\n")
                .append(plan).append("\n")
                .append(status).append("\n")
                .append(revision).append("\n")
                .append(issuedAt).append("\n")
                .append(expiresAt).append("\n")
                .append(dpEnabled ? "true" : "false").append("\n")
                .append(dpExpiresAt).append("\n")
                .append(statusReason).append("\n")
                .append("true").append("\n")
                .append("30000.00").append("\n") // agreedPrice
                .append("6000.00").append("\n")  // downPayment
                .append("24000.00").append("\n") // financedAmount
                .append("NO_COST").append("\n")  // interestType
                .append("0.00").append("\n")     // interestRate
                .append("0.00").append("\n")     // totalInterest
                .append("30000.00").append("\n") // totalPayable
                .append("6").append("\n")        // tenureMonths
                .append("MONTHLY").append("\n")  // interval
                .append("2026-10-23").append("\n") // firstDueDate
                .append("7").append("\n")        // graceDays
                .append("4000.00").append("\n")  // installmentAmount
                .append("0.00").append("\n")     // paidAmount
                .append("24000.00").append("\n") // outstandingAmount
                .append("1").append("\n")        // currentInstallment
                .append("2026-10-23").append("\n") // nextDueDate
                .append("2026-10-30").append("\n") // graceDeadline
                .append("ACTIVE").append("\n")   // emiStatus
                .append("NORMAL");               // accessStatus

        String signature = signData(canonical.toString());

        String jsonPayload = "{"
                + "\"schemaVersion\": 3,"
                + "\"licenseId\": \"" + licenseId + "\","
                + "\"machineId\": \"" + machineId + "\","
                + "\"customerName\": \"" + customerName + "\","
                + "\"product\": \"" + product + "\","
                + "\"edition\": \"" + edition + "\","
                + "\"plan\": \"" + plan + "\","
                + "\"status\": \"" + status + "\","
                + "\"revision\": 1,"
                + "\"issuedAt\": \"" + issuedAt + "\","
                + "\"expiresAt\": \"" + expiresAt + "\","
                + "\"dataProtectionEnabled\": true,"
                + "\"dataProtectionExpiresAt\": \"" + dpExpiresAt + "\","
                + "\"statusReason\": \"" + statusReason + "\","
                + "\"emi\": {"
                + "  \"enabled\": true,"
                + "  \"pricing\": {\"agreedPrice\": 30000.0, \"downPayment\": 6000.0, \"financedAmount\": 24000.0, \"interestType\": \"NO_COST\", \"interestRate\": 0.0, \"totalInterest\": 0.0, \"totalPayable\": 30000.0},"
                + "  \"schedule\": {\"tenureMonths\": 6, \"interval\": \"MONTHLY\", \"firstDueDate\": \"2026-10-23\", \"graceDays\": 7, \"installmentAmount\": 4000.0},"
                + "  \"state\": {\"paidAmount\": 0.0, \"outstandingAmount\": 24000.0, \"currentInstallment\": 1, \"nextDueDate\": \"2026-10-23\", \"graceDeadline\": \"2026-10-30\", \"emiStatus\": \"ACTIVE\", \"accessStatus\": \"NORMAL\"}"
                + "},"
                + "\"signature\": \"" + signature + "\""
                + "}";

        LicensePayload payload = objectMapper.readValue(jsonPayload, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, machineId);
        assertEquals(ValidationResult.VALID, result, "Schema 3 cross-implementation signature verification must be VALID");
    }

    @Test
    void testManagementGeneratedSchema3NonEmiLicenseVerification() throws Exception {
        String licenseId = "LIC-102";
        String machineId = "TEST-MID-2026-BBBB";
        String customerName = "Sunrise Cafe";
        String product = "RupeeCRM";
        String edition = "PRO";
        String plan = "GOLD";
        String status = "ACTIVE";
        int revision = 1;
        String issuedAt = "2026-09-23T10:00:00Z";
        String expiresAt = "LIFETIME";
        boolean dpEnabled = false;
        String dpExpiresAt = "LIFETIME";
        String statusReason = "Lifetime Non-EMI Purchase";

        StringBuilder canonical = new StringBuilder();
        canonical.append("3\n")
                .append(licenseId).append("\n")
                .append(machineId).append("\n")
                .append(customerName).append("\n")
                .append(product).append("\n")
                .append(edition).append("\n")
                .append(plan).append("\n")
                .append(status).append("\n")
                .append(revision).append("\n")
                .append(issuedAt).append("\n")
                .append(expiresAt).append("\n")
                .append("false").append("\n")
                .append("LIFETIME").append("\n")
                .append(statusReason).append("\n")
                .append("false");

        String signature = signData(canonical.toString());

        String jsonPayload = "{"
                + "\"schemaVersion\": 3,"
                + "\"licenseId\": \"" + licenseId + "\","
                + "\"machineId\": \"" + machineId + "\","
                + "\"customerName\": \"" + customerName + "\","
                + "\"product\": \"" + product + "\","
                + "\"edition\": \"" + edition + "\","
                + "\"plan\": \"GOLD\","
                + "\"status\": \"ACTIVE\","
                + "\"revision\": 1,"
                + "\"issuedAt\": \"" + issuedAt + "\","
                + "\"expiresAt\": null,"
                + "\"dataProtectionEnabled\": false,"
                + "\"dataProtectionExpiresAt\": null,"
                + "\"statusReason\": \"" + statusReason + "\","
                + "\"emi\": {\"enabled\": false},"
                + "\"signature\": \"" + signature + "\""
                + "}";

        LicensePayload payload = objectMapper.readValue(jsonPayload, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, machineId);
        assertEquals(ValidationResult.VALID, result);
    }

    @Test
    void testTamperAgreedPriceFailsVerification() throws Exception {
        String licenseId = "LIC-103";
        String machineId = "TEST-MID-2026-CCCC";
        String customerName = "Tamper Test";
        String product = "RupeeCRM";
        String edition = "PROFESSIONAL";
        String plan = "GOLD";
        String status = "ACTIVE";
        int revision = 1;
        String issuedAt = "2026-09-23T10:00:00Z";
        String expiresAt = "2027-09-23T10:00:00Z";
        String statusReason = "EMI Purchase";

        StringBuilder canonical = new StringBuilder();
        canonical.append("3\n")
                .append(licenseId).append("\n")
                .append(machineId).append("\n")
                .append(customerName).append("\n")
                .append(product).append("\n")
                .append(edition).append("\n")
                .append(plan).append("\n")
                .append(status).append("\n")
                .append(revision).append("\n")
                .append(issuedAt).append("\n")
                .append(expiresAt).append("\n")
                .append("false\n")
                .append("LIFETIME\n")
                .append(statusReason).append("\n")
                .append("true\n")
                .append("30000.00\n") // agreedPrice
                .append("6000.00\n")
                .append("24000.00\n")
                .append("NO_COST\n")
                .append("0.00\n")
                .append("0.00\n")
                .append("30000.00\n")
                .append("6\n")
                .append("MONTHLY\n")
                .append("2026-10-23\n")
                .append("7\n")
                .append("4000.00\n")
                .append("0.00\n")
                .append("24000.00\n")
                .append("1\n")
                .append("2026-10-23\n")
                .append("2026-10-30\n")
                .append("ACTIVE\n")
                .append("NORMAL");

        String signature = signData(canonical.toString());

        // Tamper agreedPrice from 30000.0 to 25000.0 in JSON
        String tamperedJson = "{"
                + "\"schemaVersion\": 3,"
                + "\"licenseId\": \"" + licenseId + "\","
                + "\"machineId\": \"" + machineId + "\","
                + "\"customerName\": \"" + customerName + "\","
                + "\"product\": \"" + product + "\","
                + "\"edition\": \"" + edition + "\","
                + "\"plan\": \"" + plan + "\","
                + "\"status\": \"" + status + "\","
                + "\"revision\": 1,"
                + "\"issuedAt\": \"" + issuedAt + "\","
                + "\"expiresAt\": \"" + expiresAt + "\","
                + "\"dataProtectionEnabled\": false,"
                + "\"dataProtectionExpiresAt\": null,"
                + "\"statusReason\": \"" + statusReason + "\","
                + "\"emi\": {"
                + "  \"enabled\": true,"
                + "  \"pricing\": {\"agreedPrice\": 25000.0, \"downPayment\": 6000.0, \"financedAmount\": 24000.0, \"interestType\": \"NO_COST\", \"interestRate\": 0.0, \"totalInterest\": 0.0, \"totalPayable\": 30000.0},"
                + "  \"schedule\": {\"tenureMonths\": 6, \"interval\": \"MONTHLY\", \"firstDueDate\": \"2026-10-23\", \"graceDays\": 7, \"installmentAmount\": 4000.0},"
                + "  \"state\": {\"paidAmount\": 0.0, \"outstandingAmount\": 24000.0, \"currentInstallment\": 1, \"nextDueDate\": \"2026-10-23\", \"graceDeadline\": \"2026-10-30\", \"emiStatus\": \"ACTIVE\", \"accessStatus\": \"NORMAL\"}"
                + "},"
                + "\"signature\": \"" + signature + "\""
                + "}";

        LicensePayload payload = objectMapper.readValue(tamperedJson, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, machineId);
        assertEquals(ValidationResult.INVALID_SIGNATURE, result, "Tampering agreedPrice MUST result in INVALID_SIGNATURE");
    }

    @Test
    void testTamperEmiEnabledFailsVerification() throws Exception {
        String licenseId = "LIC-104";
        String machineId = "TEST-MID-2026-DDDD";
        String customerName = "Tamper Test 2";
        String product = "RupeeCRM";
        String edition = "PROFESSIONAL";
        String plan = "GOLD";
        String status = "ACTIVE";
        int revision = 1;
        String issuedAt = "2026-09-23T10:00:00Z";
        String expiresAt = "2027-09-23T10:00:00Z";

        StringBuilder canonical = new StringBuilder();
        canonical.append("3\n")
                .append(licenseId).append("\n")
                .append(machineId).append("\n")
                .append(customerName).append("\n")
                .append(product).append("\n")
                .append(edition).append("\n")
                .append(plan).append("\n")
                .append(status).append("\n")
                .append(revision).append("\n")
                .append(issuedAt).append("\n")
                .append(expiresAt).append("\n")
                .append("false\n")
                .append("LIFETIME\n")
                .append("Non-EMI\n")
                .append("false");

        String signature = signData(canonical.toString());

        // Tamper emi.enabled to true
        String tamperedJson = "{"
                + "\"schemaVersion\": 3,"
                + "\"licenseId\": \"" + licenseId + "\","
                + "\"machineId\": \"" + machineId + "\","
                + "\"customerName\": \"" + customerName + "\","
                + "\"product\": \"" + product + "\","
                + "\"edition\": \"" + edition + "\","
                + "\"plan\": \"" + plan + "\","
                + "\"status\": \"" + status + "\","
                + "\"revision\": 1,"
                + "\"issuedAt\": \"" + issuedAt + "\","
                + "\"expiresAt\": \"" + expiresAt + "\","
                + "\"dataProtectionEnabled\": false,"
                + "\"dataProtectionExpiresAt\": null,"
                + "\"statusReason\": \"Non-EMI\","
                + "\"emi\": {"
                + "  \"enabled\": true,"
                + "  \"pricing\": {\"agreedPrice\": 30000.0, \"downPayment\": 0.0, \"financedAmount\": 30000.0, \"interestType\": \"NO_COST\", \"interestRate\": 0.0, \"totalInterest\": 0.0, \"totalPayable\": 30000.0},"
                + "  \"schedule\": {\"tenureMonths\": 6, \"interval\": \"MONTHLY\", \"firstDueDate\": \"2026-10-23\", \"graceDays\": 7, \"installmentAmount\": 5000.0},"
                + "  \"state\": {\"paidAmount\": 0.0, \"outstandingAmount\": 30000.0, \"currentInstallment\": 1, \"nextDueDate\": \"2026-10-23\", \"graceDeadline\": \"2026-10-30\", \"emiStatus\": \"ACTIVE\", \"accessStatus\": \"NORMAL\"}"
                + "},"
                + "\"signature\": \"" + signature + "\""
                + "}";

        LicensePayload payload = objectMapper.readValue(tamperedJson, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, machineId);
        assertEquals(ValidationResult.INVALID_SIGNATURE, result, "Tampering emi.enabled MUST result in INVALID_SIGNATURE");
    }

    @Test
    void testTamperAccessStatusFailsVerification() throws Exception {
        String licenseId = "LIC-105";
        String machineId = "TEST-MID-2026-EEEE";
        String customerName = "Tamper Test 3";
        String product = "RupeeCRM";
        String edition = "PROFESSIONAL";
        String plan = "GOLD";
        String status = "ACTIVE";
        int revision = 1;
        String issuedAt = "2026-09-23T10:00:00Z";
        String expiresAt = "2027-09-23T10:00:00Z";

        StringBuilder canonical = new StringBuilder();
        canonical.append("3\n")
                .append(licenseId).append("\n")
                .append(machineId).append("\n")
                .append(customerName).append("\n")
                .append(product).append("\n")
                .append(edition).append("\n")
                .append(plan).append("\n")
                .append(status).append("\n")
                .append(revision).append("\n")
                .append(issuedAt).append("\n")
                .append(expiresAt).append("\n")
                .append("false\n")
                .append("LIFETIME\n")
                .append("Overdue\n")
                .append("true\n")
                .append("30000.00\n")
                .append("6000.00\n")
                .append("24000.00\n")
                .append("NO_COST\n")
                .append("0.00\n")
                .append("0.00\n")
                .append("30000.00\n")
                .append("6\n")
                .append("MONTHLY\n")
                .append("2026-10-23\n")
                .append("7\n")
                .append("4000.00\n")
                .append("0.00\n")
                .append("24000.00\n")
                .append("1\n")
                .append("2026-10-23\n")
                .append("2026-10-30\n")
                .append("OVERDUE\n")
                .append("RESTRICTED"); // Signed as RESTRICTED

        String signature = signData(canonical.toString());

        // Tamper accessStatus from RESTRICTED to NORMAL in JSON
        String tamperedJson = "{"
                + "\"schemaVersion\": 3,"
                + "\"licenseId\": \"" + licenseId + "\","
                + "\"machineId\": \"" + machineId + "\","
                + "\"customerName\": \"" + customerName + "\","
                + "\"product\": \"" + product + "\","
                + "\"edition\": \"" + edition + "\","
                + "\"plan\": \"" + plan + "\","
                + "\"status\": \"" + status + "\","
                + "\"revision\": 1,"
                + "\"issuedAt\": \"" + issuedAt + "\","
                + "\"expiresAt\": \"" + expiresAt + "\","
                + "\"dataProtectionEnabled\": false,"
                + "\"dataProtectionExpiresAt\": null,"
                + "\"statusReason\": \"Overdue\","
                + "\"emi\": {"
                + "  \"enabled\": true,"
                + "  \"pricing\": {\"agreedPrice\": 30000.0, \"downPayment\": 6000.0, \"financedAmount\": 24000.0, \"interestType\": \"NO_COST\", \"interestRate\": 0.0, \"totalInterest\": 0.0, \"totalPayable\": 30000.0},"
                + "  \"schedule\": {\"tenureMonths\": 6, \"interval\": \"MONTHLY\", \"firstDueDate\": \"2026-10-23\", \"graceDays\": 7, \"installmentAmount\": 4000.0},"
                + "  \"state\": {\"paidAmount\": 0.0, \"outstandingAmount\": 24000.0, \"currentInstallment\": 1, \"nextDueDate\": \"2026-10-23\", \"graceDeadline\": \"2026-10-30\", \"emiStatus\": \"OVERDUE\", \"accessStatus\": \"NORMAL\"}" // TAMPERED to NORMAL
                + "},"
                + "\"signature\": \"" + signature + "\""
                + "}";

        LicensePayload payload = objectMapper.readValue(tamperedJson, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, machineId);
        assertEquals(ValidationResult.INVALID_SIGNATURE, result, "Tampering accessStatus MUST result in INVALID_SIGNATURE");
    }

    @Test
    void testTamperStatusReasonFailsVerification() throws Exception {
        String licenseId = "LIC-106";
        String machineId = "TEST-MID-2026-FFFF";
        String customerName = "Tamper Test 4";
        String product = "RupeeCRM";
        String edition = "PROFESSIONAL";
        String plan = "GOLD";
        String status = "ACTIVE";
        int revision = 1;
        String issuedAt = "2026-09-23T10:00:00Z";
        String expiresAt = "2027-09-23T10:00:00Z";

        StringBuilder canonical = new StringBuilder();
        canonical.append("3\n")
                .append(licenseId).append("\n")
                .append(machineId).append("\n")
                .append(customerName).append("\n")
                .append(product).append("\n")
                .append(edition).append("\n")
                .append(plan).append("\n")
                .append(status).append("\n")
                .append(revision).append("\n")
                .append(issuedAt).append("\n")
                .append(expiresAt).append("\n")
                .append("false\n")
                .append("LIFETIME\n")
                .append("Legitimate Reason\n")
                .append("false");

        String signature = signData(canonical.toString());

        // Tamper statusReason in JSON
        String tamperedJson = "{"
                + "\"schemaVersion\": 3,"
                + "\"licenseId\": \"" + licenseId + "\","
                + "\"machineId\": \"" + machineId + "\","
                + "\"customerName\": \"" + customerName + "\","
                + "\"product\": \"" + product + "\","
                + "\"edition\": \"" + edition + "\","
                + "\"plan\": \"" + plan + "\","
                + "\"status\": \"" + status + "\","
                + "\"revision\": 1,"
                + "\"issuedAt\": \"" + issuedAt + "\","
                + "\"expiresAt\": \"" + expiresAt + "\","
                + "\"dataProtectionEnabled\": false,"
                + "\"dataProtectionExpiresAt\": null,"
                + "\"statusReason\": \"Forged Reason\"," // TAMPERED
                + "\"emi\": {\"enabled\": false},"
                + "\"signature\": \"" + signature + "\""
                + "}";

        LicensePayload payload = objectMapper.readValue(tamperedJson, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, machineId);
        assertEquals(ValidationResult.INVALID_SIGNATURE, result, "Tampering statusReason MUST result in INVALID_SIGNATURE");
    }

    @Test
    void testSchema1AndSchema2AreRejected() throws Exception {
        // Schema 1
        String schema1Json = "{"
                + "\"schemaVersion\": 1,"
                + "\"licenseId\": \"LIC-LEGACY-001\","
                + "\"machineId\": \"TEST-MID-LEGACY-1\","
                + "\"customerName\": \"Old Customer 1\","
                + "\"product\": \"RupeeCRM\","
                + "\"edition\": \"PROFESSIONAL\","
                + "\"plan\": \"GOLD\","
                + "\"status\": \"ACTIVE\","
                + "\"revision\": 1,"
                + "\"signature\": \"fake-sig\""
                + "}";
        LicensePayload payload1 = objectMapper.readValue(schema1Json, LicensePayload.class);
        ValidationResult result1 = verifier.verifyLicense(payload1, "TEST-MID-LEGACY-1");
        assertEquals(ValidationResult.CORRUPT_PAYLOAD, result1, "Schema 1 MUST be rejected as CORRUPT_PAYLOAD");

        // Schema 2
        String schema2Json = "{"
                + "\"schemaVersion\": 2,"
                + "\"licenseId\": \"LIC-LEGACY-002\","
                + "\"machineId\": \"TEST-MID-LEGACY-2\","
                + "\"customerName\": \"Old Customer 2\","
                + "\"product\": \"RupeeCRM\","
                + "\"edition\": \"PROFESSIONAL\","
                + "\"plan\": \"GOLD\","
                + "\"status\": \"ACTIVE\","
                + "\"revision\": 1,"
                + "\"dataProtectionEnabled\": true,"
                + "\"dataProtectionExpiresAt\": \"2027-09-23T10:00:00Z\","
                + "\"signature\": \"fake-sig\""
                + "}";
        LicensePayload payload2 = objectMapper.readValue(schema2Json, LicensePayload.class);
        ValidationResult result2 = verifier.verifyLicense(payload2, "TEST-MID-LEGACY-2");
        assertEquals(ValidationResult.CORRUPT_PAYLOAD, result2, "Schema 2 MUST be rejected as CORRUPT_PAYLOAD");
    }

    @Test
    void testExactRealManagementGeneratedLicenseVerifiesValid() throws Exception {
        // Exact raw JSON produced by Management Server for Machine ID RKGM-GH4X-6YX1-VVN8
        String liveManagementJson = "{\"schemaVersion\":3,\"licenseId\":\"LIC-1\",\"machineId\":\"RKGM-GH4X-6YX1-VVN8\",\"customerName\":\"TESTMACHINE\",\"product\":\"RupeeCRM\",\"edition\":\"SILVER\",\"plan\":\"SILVER\",\"status\":\"ACTIVE\",\"revision\":1,\"issuedAt\":\"2026-09-23T18:47:03.034923Z\",\"expiresAt\":null,\"dataProtectionEnabled\":true,\"dataProtectionExpiresAt\":\"2027-09-23T18:47:03.034943Z\",\"statusReason\":\"Initial License Issuance\",\"emi\":{\"enabled\":true,\"pricing\":{\"agreedPrice\":2000.0,\"downPayment\":0.0,\"financedAmount\":2000.0,\"interestType\":\"NO_COST\",\"interestRate\":0.0,\"totalInterest\":0.0,\"totalPayable\":2000.0},\"schedule\":{\"tenureMonths\":6,\"interval\":\"MONTHLY\",\"firstDueDate\":\"2026-10-24\",\"graceDays\":7,\"installmentAmount\":333.33},\"state\":{\"paidAmount\":0.0,\"outstandingAmount\":2000.0,\"currentInstallment\":1,\"nextDueDate\":\"2026-10-24\",\"graceDeadline\":\"2026-10-31\",\"emiStatus\":\"ACTIVE\",\"accessStatus\":\"NORMAL\"}},\"signature\":\"Xe+a5TgiEqnm4VCllai2BdjXzZXh2H7uyOFmi8GXfJqHJCSOEeamOAiuRuq6s06x9vqmV7oP5YmtKnd1SqefBA==\"}";

        LicenseVerifier defaultVerifier = new LicenseVerifier();
        LicensePayload payload = objectMapper.readValue(liveManagementJson, LicensePayload.class);
        ValidationResult result = defaultVerifier.verifyLicense(payload, "RKGM-GH4X-6YX1-VVN8");
        assertEquals(ValidationResult.VALID, result, "Exact real management-generated license MUST verify as VALID");
    }
}
