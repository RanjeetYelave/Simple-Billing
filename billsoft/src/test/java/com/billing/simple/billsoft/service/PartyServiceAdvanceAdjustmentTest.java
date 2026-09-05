package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.impl.PartyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PartyServiceAdvanceAdjustmentTest {

    @Mock
    private PartyRepository partyRepo;

    @Mock
    private PartyPaymentRepository partyPaymentRepo;

    @Mock
    private PurchaseOrderRepository poRepo;

    @InjectMocks
    private PartyServiceImpl partyService;

    private Long firmId = 100L;
    private Party vendor;
    private PurchaseOrder po;
    private PartyPayment advancePayment;

    @BeforeEach
    void setUp() {
        vendor = Party.builder()
                .id(1L)
                .firmId(firmId)
                .name("Supplier Alpha")
                .build();

        po = PurchaseOrder.builder()
                .id(10L)
                .firmId(firmId)
                .party(vendor)
                .poNumber("PO-2026-0001")
                .totalAmount(new BigDecimal("30000.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus("YET_TO_PAY")
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        advancePayment = PartyPayment.builder()
                .id(50L)
                .firmId(firmId)
                .partyId(vendor.getId())
                .purchaseOrderId(null)
                .amount(new BigDecimal("50000.00"))
                .paymentDate(LocalDate.now())
                .paymentMode("BANK_TRANSFER")
                .referenceNumber("ADV-TXN-101")
                .build();
    }

    @Test
    void testGetUnallocatedPayments() {
        when(partyPaymentRepo.findByFirmIdAndPartyIdAndPurchaseOrderIdIsNullOrderByPaymentDateDescIdDesc(firmId, 1L))
                .thenReturn(List.of(advancePayment));

        List<PartyPayment> advances = partyService.getUnallocatedPayments(1L, firmId);
        assertEquals(1, advances.size());
        assertNull(advances.get(0).getPurchaseOrderId());
        assertEquals(new BigDecimal("50000.00"), advances.get(0).getAmount());
    }

    @Test
    void testFullAdvanceAdjustment() {
        // Advance of exact PO total: 30000
        PartyPayment exactAdvance = PartyPayment.builder()
                .id(51L)
                .firmId(firmId)
                .partyId(vendor.getId())
                .purchaseOrderId(null)
                .amount(new BigDecimal("30000.00"))
                .build();

        when(poRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(po));
        when(partyPaymentRepo.findById(51L)).thenReturn(Optional.of(exactAdvance));
        when(partyPaymentRepo.save(any(PartyPayment.class))).thenAnswer(i -> i.getArgument(0));
        when(poRepo.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        // After linking, partyPaymentRepo.findByPurchaseOrderId returns the updated payment
        when(partyPaymentRepo.findByPurchaseOrderId(10L)).thenReturn(List.of(exactAdvance));

        PurchaseOrder updatedPo = partyService.adjustAdvancePayment(10L, 51L, new BigDecimal("30000.00"), "Full advance adjust", firmId);

        assertEquals(10L, exactAdvance.getPurchaseOrderId());
        assertEquals(new BigDecimal("30000.00"), updatedPo.getPaidAmount());
        assertEquals("PAID", updatedPo.getPaymentStatus());
        verify(partyPaymentRepo).save(exactAdvance);
    }

    @Test
    void testPartialAdvanceAdjustmentWithSplit() {
        // PO is 30000, Advance is 50000 -> Adjust 30000, leaving 20000 advance
        when(poRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(po));
        when(partyPaymentRepo.findById(50L)).thenReturn(Optional.of(advancePayment));
        when(partyPaymentRepo.save(any(PartyPayment.class))).thenAnswer(i -> i.getArgument(0));
        when(poRepo.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PartyPayment allocatedPart = PartyPayment.builder()
                .id(52L)
                .firmId(firmId)
                .partyId(vendor.getId())
                .purchaseOrderId(10L)
                .amount(new BigDecimal("30000.00"))
                .build();
        when(partyPaymentRepo.findByPurchaseOrderId(10L)).thenReturn(List.of(allocatedPart));

        PurchaseOrder updatedPo = partyService.adjustAdvancePayment(10L, 50L, new BigDecimal("30000.00"), "Adjusted partial", firmId);

        // Original advance payment reduced to remaining 20000
        assertEquals(new BigDecimal("20000.00"), advancePayment.getAmount());
        assertNull(advancePayment.getPurchaseOrderId());

        assertEquals(new BigDecimal("30000.00"), updatedPo.getPaidAmount());
        assertEquals("PAID", updatedPo.getPaymentStatus());

        // Verify save was called for original payment (reduced) AND the new allocated entry
        verify(partyPaymentRepo, times(2)).save(any(PartyPayment.class));
    }

    @Test
    void testUnadjustPayment() {
        PartyPayment allocated = PartyPayment.builder()
                .id(55L)
                .firmId(firmId)
                .partyId(vendor.getId())
                .purchaseOrderId(10L)
                .amount(new BigDecimal("30000.00"))
                .build();

        po.setPaidAmount(new BigDecimal("30000.00"));
        po.setPaymentStatus("PAID");

        when(partyPaymentRepo.findById(55L)).thenReturn(Optional.of(allocated));
        when(partyPaymentRepo.save(any(PartyPayment.class))).thenAnswer(i -> i.getArgument(0));
        when(poRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(po));
        when(poRepo.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        // After unadjusting, no payments linked to PO
        when(partyPaymentRepo.findByPurchaseOrderId(10L)).thenReturn(new ArrayList<>());

        PurchaseOrder updatedPo = partyService.unadjustPayment(55L, firmId);

        assertNull(allocated.getPurchaseOrderId());
        assertEquals(BigDecimal.ZERO, updatedPo.getPaidAmount());
        assertEquals("YET_TO_PAY", updatedPo.getPaymentStatus());
        verify(partyPaymentRepo).save(allocated);
    }

    @Test
    void testAdjustAdvanceCrossVendorOrExcessRejection() {
        when(poRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(po));

        // Mismatched vendor payment
        PartyPayment diffVendorPay = PartyPayment.builder()
                .id(99L)
                .firmId(firmId)
                .partyId(999L) // different party
                .amount(new BigDecimal("50000.00"))
                .build();
        when(partyPaymentRepo.findById(99L)).thenReturn(Optional.of(diffVendorPay));

        assertThrows(IllegalArgumentException.class, () ->
                partyService.adjustAdvancePayment(10L, 99L, new BigDecimal("10000.00"), null, firmId)
        );

        // Excess adjustment amount
        when(partyPaymentRepo.findById(50L)).thenReturn(Optional.of(advancePayment));
        assertThrows(IllegalArgumentException.class, () ->
                partyService.adjustAdvancePayment(10L, 50L, new BigDecimal("60000.00"), null, firmId) // exceeds 50000
        );
    }
}
