package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoicePayment;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class InvoicePaymentTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository paymentRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Test
    void testPartialAndFullPaymentFlow() {
        Customer cust = new Customer();
        cust.setName("Payment Test Customer");
        cust.setFirmId(1L);
        cust = customerRepo.save(cust);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(cust.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setQty(2);
        item.setPricePerUnit(BigDecimal.valueOf(500)); // Total 1000
        req.setItems(List.of(item));

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getPaid()).isFalse();

        // 1. Partial payment of 400
        InvoicePayment p1 = invoiceService.recordPayment(invoice.getId(), BigDecimal.valueOf(400), LocalDate.now(), "Cash", "REC-001", "Initial deposit");
        assertThat(p1.getId()).isNotNull();

        Invoice afterP1 = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertThat(afterP1.getPaid()).isFalse();

        List<InvoicePayment> payments = invoiceService.getPayments(invoice.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));

        // 2. Remaining payment of 600
        InvoicePayment p2 = invoiceService.recordPayment(invoice.getId(), BigDecimal.valueOf(600), LocalDate.now(), "UPI", "UPI-12345", "Settlement");
        assertThat(p2.getId()).isNotNull();

        Invoice afterP2 = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertThat(afterP2.getPaid()).isTrue();

        List<InvoicePayment> allPayments = invoiceService.getPayments(invoice.getId());
        assertThat(allPayments).hasSize(2);
    }
}
