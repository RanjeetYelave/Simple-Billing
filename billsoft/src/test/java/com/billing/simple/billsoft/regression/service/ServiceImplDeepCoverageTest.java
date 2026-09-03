package com.billing.simple.billsoft.regression.service;

import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Service Implementation Layer (Party, PurchaseOrder, BusinessLetter) Deep Coverage Tests")
class ServiceImplDeepCoverageTest {

    @Autowired
    private PartyService partyService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private BusinessLetterService letterService;

    @Autowired
    private ProductService productService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    private FirmDetails testFirm;
    private Party testParty;
    private Product testProduct;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        testFirm = new FirmDetails();
        testFirm.setFirmName("Precision Component Works");
        testFirm.setGstin("27AAACP5566D1Z1");
        testFirm.setAddressLine1("MIDC Bhosari");
        testFirm.setPhone("9811889900");
        testFirm.setEmail("orders@precisionworks.com");
        testFirm = firmService.create(testFirm);

        testParty = partyService.createParty(Party.builder()
                .name("Steel Craft Alloys")
                .contactPerson("Sunil Patil")
                .phone("9822334411")
                .email("sales@steelcraft.com")
                .address("Plot 12 MIDC")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411026")
                .gstin("27AABCS1234D1Z9")
                .openingBalance(BigDecimal.valueOf(10000.0))
                .openingBalanceType("PAYABLE")
                .firmId(testFirmId)
                .build());

        testProduct = productService.create(Product.builder()
                .name("Grade 304 Stainless Rods")
                .price(BigDecimal.valueOf(800.0))
                .costPrice(BigDecimal.valueOf(600.0))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(testFirmId)
                .build());
    }

    @Test
    @DisplayName("Should test PartyService CRUD, search, financial summaries, and payments")
    void testPartyServiceFullLifecycle() {
        // 1. Update Party
        testParty.setContactPerson("Sunil R. Patil");
        Party updated = partyService.updateParty(testParty.getId(), testParty);
        assertThat(updated.getContactPerson()).isEqualTo("Sunil R. Patil");

        // 2. Query & Search
        assertThat(partyService.getPartyById(testParty.getId(), testFirmId)).isPresent();
        assertThat(partyService.getPartiesByFirm(testFirmId)).isNotEmpty();

        // 3. Record Party Payment
        PartyPayment payment = PartyPayment.builder()
                .partyId(testParty.getId())
                .paymentDate(LocalDate.now())
                .amount(BigDecimal.valueOf(5000.0))
                .paymentMode("BANK_TRANSFER")
                .referenceNumber("NEFT-990011")
                .notes("Advance for raw materials")
                .firmId(testFirmId)
                .build();
        PartyPayment savedPayment = partyService.recordPayment(payment);
        assertThat(savedPayment.getId()).isNotNull();

        // 4. Financial Summary
        PartyFinancialSummary summary = partyService.getFinancialSummary(testParty.getId(), testFirmId);
        assertThat(summary.getPartyId()).isEqualTo(testParty.getId());
        assertThat(summary.getTotalPaid()).isNotNull();

        List<PartyFinancialSummary> allSummaries = partyService.getAllPartiesWithFinancialSummaries(testFirmId);
        assertThat(allSummaries).isNotEmpty();

        // 5. Query and delete payment
        List<PartyPayment> payments = partyService.getPaymentsByParty(testParty.getId(), testFirmId);
        assertThat(payments).hasSize(1);

        partyService.deletePayment(savedPayment.getId(), testFirmId);
        assertThat(partyService.getPaymentsByParty(testParty.getId(), testFirmId)).isEmpty();

        // 6. Delete party
        partyService.deleteParty(testParty.getId(), testFirmId);
        assertThat(partyService.getPartyById(testParty.getId(), testFirmId)).isEmpty();
    }

    @Test
    @DisplayName("Should test PurchaseOrderService creation, PO number generator, status transitions to RECEIVED, and stock sync")
    void testPurchaseOrderServiceFullLifecycle() throws Exception {
        // 1. PO number generation
        String poNum = poService.generateNextPoNumber(testFirmId);
        assertThat(poNum).startsWith("PO-");

        // 2. Create PO
        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .description("Grade 304 Stainless Rods - 10mm")
                .quantity(BigDecimal.valueOf(20.0))
                .unitPrice(BigDecimal.valueOf(600.0))
                .gstPercent(BigDecimal.valueOf(18.0))
                .taxableAmount(BigDecimal.valueOf(12000.0))
                .build();

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(poNum)
                .poDate(LocalDate.now())
                .expectedDeliveryDate(LocalDate.now().plusDays(7))
                .party(testParty)
                .status(PurchaseOrderStatus.ISSUED)
                .items(List.of(item))
                .totalAmount(BigDecimal.valueOf(14160.0))
                .firmId(testFirmId)
                .build();

        PurchaseOrder createdPo = poService.createPurchaseOrder(po);
        assertThat(createdPo.getId()).isNotNull();

        // Initial product stock was 50
        Product currentProd = productService.getById(testProduct.getId());
        assertThat(currentProd.getStockQuantity()).isEqualByComparingTo("50.0");

        // 3. Mark PO as RECEIVED (should automatically increase product stock +20 = 70)
        PurchaseOrder receivedPo = poService.updateStatus(createdPo.getId(), testFirmId, PurchaseOrderStatus.RECEIVED);
        assertThat(receivedPo.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);

        Product updatedProd = productService.getById(testProduct.getId());
        assertThat(updatedProd.getStockQuantity()).isEqualByComparingTo("70.0");

        // 4. Query PO & Generate PDF
        assertThat(poService.getPurchaseOrderById(createdPo.getId(), testFirmId)).isPresent();
        assertThat(poService.getPurchaseOrdersByFirm(testFirmId)).isNotEmpty();
        assertThat(poService.getPurchaseOrdersByParty(testFirmId, testParty.getId())).isNotEmpty();

        byte[] poPdf = poService.generatePoPdf(createdPo.getId(), testFirmId);
        assertThat(poPdf).isNotNull();
        assertThat(poPdf.length).isGreaterThan(1000);

        // 5. Delete PO
        poService.deletePurchaseOrder(createdPo.getId(), testFirmId);
        assertThat(poService.getPurchaseOrderById(createdPo.getId(), testFirmId)).isEmpty();
    }

    @Test
    @DisplayName("Should test BusinessLetterService drafting, numbering, queries, status updates and deletion")
    void testBusinessLetterServiceFullLifecycle() throws Exception {
        // 1. Generate Letter Number
        String letterNum = letterService.generateNextLetterNumber(testFirmId);
        assertThat(letterNum).startsWith("LTR-");

        // 2. Draft Letter
        BusinessLetter letter = BusinessLetter.builder()
                .letterNumber(letterNum)
                .letterDate(LocalDate.now())
                .firmId(testFirmId)
                .senderType("FIRM")
                .recipientType(LetterRecipientType.PARTY)
                .partyId(testParty.getId())
                .recipientName(testParty.getName())
                .recipientAddress(testParty.getAddress())
                .subject("Raw Material Dispatch Schedule Notice")
                .category("NOTICE")
                .content("Please confirm delivery of scheduled consignment by this Friday.")
                .signatoryName("Operations Manager")
                .signatoryDesignation("Head of Supply Chain")
                .status(LetterStatus.DRAFT)
                .build();

        BusinessLetter saved = letterService.createLetter(letter);
        assertThat(saved.getId()).isNotNull();

        // 3. Update Letter
        saved.setStatus(LetterStatus.ISSUED);
        saved.setContent("Please confirm delivery of scheduled consignment by this Friday without fail.");
        BusinessLetter updated = letterService.updateLetter(saved.getId(), saved);
        assertThat(updated.getStatus()).isEqualTo(LetterStatus.ISSUED);

        // 4. Query & Generate PDF
        assertThat(letterService.getLetterById(saved.getId(), testFirmId)).isPresent();
        assertThat(letterService.getLettersByFirm(testFirmId, null, null, null, null, null, null)).isNotEmpty();

        byte[] letterPdf = letterService.generateLetterPdf(saved.getId(), testFirmId);
        assertThat(letterPdf).isNotNull();
        assertThat(letterPdf.length).isGreaterThan(1000);

        // 5. Delete
        letterService.deleteLetter(saved.getId(), testFirmId);
        assertThat(letterService.getLetterById(saved.getId(), testFirmId)).isEmpty();
    }
}
