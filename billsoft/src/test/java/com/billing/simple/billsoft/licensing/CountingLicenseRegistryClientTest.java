package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.controllers.LicensingController;
import com.billing.simple.billsoft.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CountingLicenseRegistryClientTest {

    @TempDir
    File tempDir;

    private KeyPair keyPair;
    private MachineIdentity machineIdentity;
    private LicenseVerifier licenseVerifier;
    private LicenseStorage licenseStorage;
    private LicenseCoordinator coordinator;
    private LicensingController controller;
    private final AtomicInteger registryRequestCount = new AtomicInteger(0);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        registryRequestCount.set(0);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();

        File midFile = new File(tempDir, "mid.dat");
        this.machineIdentity = new MachineIdentity(midFile);

        this.licenseVerifier = new LicenseVerifier(keyPair.getPublic());
        this.licenseStorage = new LicenseStorage(tempDir);
        this.coordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage);

        // Plug in the counting test double
        this.coordinator.setRegistryFetcher(path -> {
            registryRequestCount.incrementAndGet();
            try {
                LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
                return mapper.writeValueAsString(lic);
            } catch (Exception e) {
                return null;
            }
        });

        this.controller = new LicensingController(coordinator, null, null);
    }

    private LicensePayload createSignedSchema3License(String mid, int rev, String emiStatus, String accessStatus) throws Exception {
        LicensePayload p = new LicensePayload();
        p.setSchemaVersion(3);
        p.setLicenseId("LIC-TEST-100");
        p.setMachineId(mid);
        p.setCustomerName("Test Retailer");
        p.setProduct("RupeeCRM");
        p.setEdition("PROFESSIONAL");
        p.setPlan(MembershipPlan.GOLD);
        p.setStatus(LicenseStatus.ACTIVE);
        p.setRevision(rev);
        p.setIssuedAt(Instant.now());
        p.setExpiresAt(null);
        p.setDataProtectionEnabled(true);
        p.setDataProtectionExpiresAt(null);

        EmiPayload emi = new EmiPayload();
        emi.setEnabled(true);

        EmiPricingPayload pricing = new EmiPricingPayload();
        pricing.setAgreedPrice(new BigDecimal("20000.00"));
        pricing.setDownPayment(new BigDecimal("5000.00"));
        pricing.setFinancedAmount(new BigDecimal("15000.00"));
        pricing.setInterestType("NO_COST");
        pricing.setInterestRate(new BigDecimal("0.00"));
        pricing.setTotalInterest(new BigDecimal("0.00"));
        pricing.setTotalPayable(new BigDecimal("20000.00"));
        emi.setPricing(pricing);

        EmiSchedulePayload sched = new EmiSchedulePayload();
        sched.setTenureMonths(3);
        sched.setInterval("MONTHLY");
        sched.setFirstDueDate(LocalDate.now().plusMonths(1).toString());
        sched.setGraceDays(7);
        sched.setInstallmentAmount(new BigDecimal("5000.00"));
        emi.setSchedule(sched);

        EmiStatePayload st = new EmiStatePayload();
        st.setPaidAmount(new BigDecimal("5000.00"));
        st.setOutstandingAmount(new BigDecimal("10000.00"));
        st.setCurrentInstallment(2);
        st.setNextDueDate(LocalDate.now().plusMonths(1).toString());
        st.setGraceDeadline(LocalDate.now().plusMonths(1).plusDays(7).toString());
        st.setEmiStatus(emiStatus);
        st.setAccessStatus(accessStatus);
        emi.setState(st);

        p.setEmi(emi);

        String canonical = LicenseVerifier.buildCanonicalString(p);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        p.setSignature(Base64.getEncoder().encodeToString(signer.sign()));

        return p;
    }

    @Test
    void testHundredDashboardOpensProduceZeroRegistryRequests() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);

        registryRequestCount.set(0);

        for (int i = 0; i < 100; i++) {
            controller.getStatus();
        }

        assertEquals(0, registryRequestCount.get(), "100 dashboard/status opens MUST produce exactly 0 registry requests");
    }

    @Test
    void testHundredPopupChecksProduceZeroRegistryRequests() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);

        registryRequestCount.set(0);

        for (int i = 0; i < 100; i++) {
            Long days = coordinator.getLicenseDaysRemaining();
            Long dpDays = coordinator.getDataProtectionDaysRemaining();
            boolean dpActive = coordinator.isDataProtectionActive();
            controller.getStatus();
        }

        assertEquals(0, registryRequestCount.get(), "100 popup/countdown calculations MUST produce exactly 0 registry requests");
    }

    @Test
    void testCadenceNotReachedProducesZeroRequests() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);

        // Mark checked today
        licenseStorage.updateSyncState(LocalDate.now().toString(), 1, "LIC-TEST-100");
        registryRequestCount.set(0);

        coordinator.syncWithRegistry(false);
        assertEquals(0, registryRequestCount.get(), "Sync within cadence interval MUST produce 0 requests");
    }

    @Test
    void testCadenceReachedProducesExactlyOneRequest() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);

        // Mark checked 10 days ago (cadence reached for active EMI which is 3 days)
        licenseStorage.updateSyncState(LocalDate.now().minusDays(10).toString(), 1, "LIC-TEST-100");
        registryRequestCount.set(0);

        coordinator.syncWithRegistry(false);
        assertEquals(1, registryRequestCount.get(), "Sync after cadence interval MUST produce exactly 1 request");
    }

    @Test
    void testManualIHavePaidEmiProducesExactlyOneRequest() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);

        licenseStorage.updateSyncState(LocalDate.now().toString(), 1, "LIC-TEST-100");
        registryRequestCount.set(0);

        controller.recoverEmi();
        assertEquals(1, registryRequestCount.get(), "Manual 'I HAVE PAID EMI' recovery MUST produce exactly 1 request");
    }

    @Test
    void testTenConcurrentRecoveryClicksProduceExactlyOneRequest() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);

        // Simulate slower network fetch to test in-flight mutex concurrency
        coordinator.setRegistryFetcher(path -> {
            registryRequestCount.incrementAndGet();
            try {
                Thread.sleep(50);
                LicensePayload remote = createSignedSchema3License(machineIdentity.getMachineId(), 2, "SETTLED", "NORMAL");
                return mapper.writeValueAsString(remote);
            } catch (Exception e) {
                return null;
            }
        });

        registryRequestCount.set(0);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    controller.recoverEmi();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        assertEquals(1, registryRequestCount.get(), "10 concurrent recovery clicks MUST result in exactly 1 registry request due to in-flight mutex");
    }

    @Test
    void testNetworkFailurePreservesLocalEntitlement() throws Exception {
        LicensePayload lic = createSignedSchema3License(machineIdentity.getMachineId(), 1, "ACTIVE", "NORMAL");
        coordinator.activateLicense(lic);
        assertEquals(ValidationResult.VALID, coordinator.getCurrentValidationResult());

        // Make network fail
        coordinator.setRegistryFetcher(path -> {
            throw new RuntimeException("Network / DNS unreachable");
        });

        controller.recoverEmi();

        // Local license must remain valid and intact
        assertEquals(ValidationResult.VALID, coordinator.getCurrentValidationResult());
        assertNotNull(licenseStorage.loadLocalLicense(), "Local license file MUST be preserved on network failure");
    }
}
