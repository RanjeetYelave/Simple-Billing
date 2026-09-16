package com.billing.simple.billsoft.regression.purchase;

import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.service.PartyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Party & Vendor Ledger Regression Tests")
class PartyLedgerRegressionTest {

    @Autowired
    private PartyService partyService;

    private final Long testFirmId = 1L;

    @Test
    @DisplayName("Should create party, record payment ledger entries, and update outstanding balance")
    void shouldCreatePartyAndRecordPayments() {
        Party supplier = partyService.createParty(Party.builder()
                .name("Modern Industrial Supplies")
                .openingBalance(BigDecimal.valueOf(10000.00))
                .firmId(testFirmId)
                .build());

        assertThat(supplier.getOpeningBalance()).isEqualByComparingTo("10000.00");

        // Record a payment to supplier
        PartyPayment payment = partyService.recordPayment(PartyPayment.builder()
                .partyId(supplier.getId())
                .firmId(testFirmId)
                .amount(BigDecimal.valueOf(4000.00))
                .paymentDate(LocalDate.now())
                .paymentMode("BANK_TRANSFER")
                .referenceNumber("NEFT-889900")
                .notes("Advance against PO-001")
                .build());

        assertThat(payment.getId()).isNotNull();

        List<PartyPayment> payments = partyService.getPaymentsByParty(supplier.getId(), testFirmId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Should list all parties by firm ID")
    void shouldFilterPartiesByFirm() {
        partyService.createParty(Party.builder()
                .name("Apex Retailer")
                .firmId(testFirmId)
                .build());

        partyService.createParty(Party.builder()
                .name("Apex Fabricator")
                .firmId(testFirmId)
                .build());

        List<Party> parties = partyService.getPartiesByFirm(testFirmId);

        assertThat(parties).extracting(Party::getName).contains("Apex Retailer", "Apex Fabricator");
    }
}
