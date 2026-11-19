package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class LicenseIntegrationTest {

    @TempDir
    File tempDir;

    private ObjectMapper mapper;
    private LicenseVerifier verifier;
    private final String TARGET_MID = "XPRR-05ZD-6RT6-KM73";

    // Exact Schema 3 payload activated by SMT for Demo Machine - MAC
    private final String SMT_ISSUED_LICENSE_JSON =
            "{\"schemaVersion\":3,\"licenseId\":\"LIC-1\",\"machineId\":\"XPRR-05ZD-6RT6-KM73\",\"customerName\":\"Demo Machine - MAC\",\"product\":\"RupeeCRM\",\"edition\":\"GOLD\",\"plan\":\"GOLD\",\"status\":\"ACTIVE\",\"revision\":1,\"issuedAt\":\"2026-11-19T11:29:31.821315Z\",\"expiresAt\":null,\"dataProtectionEnabled\":true,\"dataProtectionExpiresAt\":\"2029-11-19T11:29:31.821364Z\",\"statusReason\":\"Initial License Issuance\",\"emi\":{\"enabled\":true,\"pricing\":{\"agreedPrice\":1E+3,\"downPayment\":0,\"financedAmount\":1E+3,\"interestType\":\"NO_COST\",\"interestRate\":0,\"totalInterest\":0,\"totalPayable\":1E+3},\"schedule\":{\"tenureMonths\":12,\"interval\":\"MONTHLY\",\"firstDueDate\":\"2026-12-19\",\"graceDays\":7,\"installmentAmount\":83.33},\"state\":{\"paidAmount\":0,\"outstandingAmount\":1E+3,\"currentInstallment\":1,\"nextDueDate\":\"2026-12-19\",\"graceDeadline\":\"2026-12-26\",\"emiStatus\":\"ACTIVE\",\"accessStatus\":\"NORMAL\"}},\"signature\":\"TaQrFUUYXuCvlqrEqxmEuOavp5vPRGfaE4V81iTaF1oCRQwXofLFnsp05KmZXOT0FyfxCXPxfUhNCBPv2v/rAw==\"}";

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        verifier = new LicenseVerifier();
    }

    @Test
    void testSmtIssuedLicense_DeserializationAndSchema3Validation() throws Exception {
        LicensePayload payload = mapper.readValue(SMT_ISSUED_LICENSE_JSON, LicensePayload.class);
        assertNotNull(payload);
        assertEquals(3, payload.getSchemaVersion());
        assertEquals("LIC-1", payload.getLicenseId());
        assertEquals(TARGET_MID, payload.getMachineId());
        assertEquals("Demo Machine - MAC", payload.getCustomerName());
        assertEquals(MembershipPlan.GOLD, payload.getPlan());
        assertEquals(LicenseStatus.ACTIVE, payload.getStatus());
        assertEquals(1, payload.getRevision());
        assertNull(payload.getExpiresAt(), "GOLD plan must have null expiry (Lifetime)");
        assertTrue(payload.getDataProtectionEnabled());
        assertNotNull(payload.getDataProtectionExpiresAt());

        // Validate EMI details
        assertNotNull(payload.getEmi());
        assertTrue(payload.getEmi().getEnabled());
        assertEquals("ACTIVE", payload.getEmi().getState().getEmiStatus());
        assertEquals("NORMAL", payload.getEmi().getState().getAccessStatus());
        assertEquals(12, payload.getEmi().getSchedule().getTenureMonths());
        assertEquals(83.33, payload.getEmi().getSchedule().getInstallmentAmount().doubleValue(), 0.01);
    }

    @Test
    void testSmtIssuedLicense_CryptographicSignatureVerification_Passes() throws Exception {
        LicensePayload payload = mapper.readValue(SMT_ISSUED_LICENSE_JSON, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, TARGET_MID);
        assertEquals(ValidationResult.VALID, result, "SMT issued license signature must be cryptographically valid");
        assertTrue(result.isValid());
    }

    @Test
    void testSmtIssuedLicense_WrongMachineId_Rejected() throws Exception {
        LicensePayload payload = mapper.readValue(SMT_ISSUED_LICENSE_JSON, LicensePayload.class);
        ValidationResult result = verifier.verifyLicense(payload, "WRONG-MID-1234-5678");
        assertEquals(ValidationResult.MACHINE_MISMATCH, result, "License bound to different MID must be rejected");
        assertFalse(result.isValid());
    }

    @Test
    void testSmtIssuedLicense_TamperedPayload_Rejected() throws Exception {
        LicensePayload payload = mapper.readValue(SMT_ISSUED_LICENSE_JSON, LicensePayload.class);
        // Tamper with customer name or revision
        payload.setCustomerName("Hacked Customer");
        ValidationResult result = verifier.verifyLicense(payload, TARGET_MID);
        assertEquals(ValidationResult.INVALID_SIGNATURE, result, "Tampered payload must fail cryptographic signature check");
        assertFalse(result.isValid());
    }

    @Test
    void testSmtIssuedLicense_TamperedEmi_Rejected() throws Exception {
        LicensePayload payload = mapper.readValue(SMT_ISSUED_LICENSE_JSON, LicensePayload.class);
        // Tamper with EMI tenure
        payload.getEmi().getSchedule().setTenureMonths(24);
        ValidationResult result = verifier.verifyLicense(payload, TARGET_MID);
        assertEquals(ValidationResult.INVALID_SIGNATURE, result, "Tampered EMI tenure must fail signature verification");
        assertFalse(result.isValid());
    }

    @Test
    void testSmtIssuedLicense_DataProtectionActive() throws Exception {
        LicensePayload payload = mapper.readValue(SMT_ISSUED_LICENSE_JSON, LicensePayload.class);
        boolean dpActive = verifier.isDataProtectionActive(payload, Instant.now());
        assertTrue(dpActive, "Data protection must be active when enabled and expiry is in the future");
    }

    @Test
    void testCoordinatorFallback_PullsLicenseWhenPrimaryFails() throws Exception {
        File midFile = new File(tempDir, "mid.dat");
        Files.writeString(midFile.toPath(), TARGET_MID);
        MachineIdentity mid = new MachineIdentity(midFile);
        LicenseStorage storage = new LicenseStorage(tempDir);
        SnoozeManager snooze = new SnoozeManager(tempDir);

        LicenseCoordinator coordinator = new LicenseCoordinator(mid, verifier, storage, snooze) {
            @Override
            public ClassifiedFetchResult fetchHttpClassified(String urlStr, long deadlineNanos) {
                if (urlStr.contains("raw.githubusercontent.com")) {
                    // Simulate ISP connection timeout on primary raw URL
                    return new ClassifiedFetchResult(FetchClassification.CONNECT_TIMEOUT, -1, null, 1500);
                } else if (urlStr.contains("api.github.com")) {
                    // Simulate successful API contents fallback response
                    return new ClassifiedFetchResult(FetchClassification.SUCCESS, 200, SMT_ISSUED_LICENSE_JSON, 200);
                }
                return new ClassifiedFetchResult(FetchClassification.RESOURCE_NOT_FOUND, 404, null, 50);
            }
        };

        boolean synced = coordinator.syncWithRegistry(true);
        assertTrue(synced, "Sync should succeed via API fallback when primary times out");
        assertEquals(ValidationResult.VALID, coordinator.getCurrentValidationResult());
        assertNotNull(coordinator.getActiveLicense());
        assertEquals("LIC-1", coordinator.getActiveLicense().getLicenseId());
        assertEquals(MembershipPlan.GOLD, coordinator.getActiveLicense().getPlan());
        assertTrue(coordinator.isDataProtectionActive());

        // Verify local license file was saved
        assertTrue(storage.getLicenseFile().exists(), "License file should be saved to local storage");
        LicensePayload localSaved = storage.loadLocalLicense();
        assertNotNull(localSaved);
        assertEquals("LIC-1", localSaved.getLicenseId());
    }
}
