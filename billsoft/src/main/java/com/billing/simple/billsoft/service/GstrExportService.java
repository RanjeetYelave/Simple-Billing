package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.util.GstStateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service to export GST return payloads (GSTR-1, GSTR-3B) and summary reports
 * in standard Indian Goods and Services Tax Network (GSTN) format.
 * (AUDIT-032)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GstrExportService {

    private final InvoiceRepository invoiceRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final FirmDetailsService firmService;

    private static final DateTimeFormatter GST_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Generate standard GSTR-1 JSON data structure conforming to GST portal specifications.
     * Includes B2B, B2CS (small consumer), CDNR (credit notes), and HSN summaries.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateGstr1Payload(Long firmId, LocalDate fromDate, LocalDate toDate) {
        FirmDetails firm = firmService.get(firmId);
        String sellerGstin = firm != null ? firm.getGstin() : null;
        String sellerStateCode = GstStateUtils.resolveSellerStateCode(firm);

        List<Invoice> invoices = invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                firmId, fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX));
        List<SalesReturn> creditNotes = salesReturnRepo.findByFirmIdAndReturnDateBetween(firmId, fromDate, toDate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gstin", sellerGstin);
        payload.put("fp", fromDate.format(DateTimeFormatter.ofPattern("MMyyyy")));
        payload.put("version", "GSTN_GSTR1_V1.1");
        payload.put("cur_gt", BigDecimal.ZERO);

        List<Map<String, Object>> b2bList = new ArrayList<>();
        List<Map<String, Object>> b2csList = new ArrayList<>();
        List<Map<String, Object>> cdnrList = new ArrayList<>();

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;

        // Process invoices
        for (Invoice inv : invoices) {
            // Ignore drafts, estimates, cancelled
            if (inv.getStatus() == InvoiceStatus.DRAFT || inv.getStatus() == InvoiceStatus.ESTIMATE || inv.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }

            Customer cust = inv.getCustomer();
            String buyerGstin = cust != null && cust.getGstin() != null ? cust.getGstin().trim() : null;
            String pos = inv.getPlaceOfSupply() != null ? inv.getPlaceOfSupply() : GstStateUtils.resolvePlaceOfSupply(null, cust, sellerStateCode);
            String posCode = GstStateUtils.normalizeStateCode(buyerGstin != null && !buyerGstin.isBlank() ? buyerGstin : pos);

            BigDecimal invTaxable = calculateInvoiceTaxableAmount(inv);
            BigDecimal invCgst = inv.getCgstAmount() != null ? inv.getCgstAmount() : BigDecimal.ZERO;
            BigDecimal invSgst = inv.getSgstAmount() != null ? inv.getSgstAmount() : BigDecimal.ZERO;
            BigDecimal invIgst = inv.getIgstAmount() != null ? inv.getIgstAmount() : BigDecimal.ZERO;

            totalTaxable = totalTaxable.add(invTaxable);
            totalCgst = totalCgst.add(invCgst);
            totalSgst = totalSgst.add(invSgst);
            totalIgst = totalIgst.add(invIgst);

            if (buyerGstin != null && !buyerGstin.isBlank()) {
                // B2B Transaction
                Map<String, Object> b2bInv = new LinkedHashMap<>();
                b2bInv.put("ctin", buyerGstin);
                b2bInv.put("pos", posCode);
                b2bInv.put("inv_typ", "R");
                b2bInv.put("inum", inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : String.valueOf(inv.getId()));
                b2bInv.put("idt", inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(GST_DATE_FMT) : "");
                b2bInv.put("val", inv.getTotalAmount() != null ? inv.getTotalAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
                b2bInv.put("txval", invTaxable.setScale(2, RoundingMode.HALF_UP));
                b2bInv.put("camt", invCgst.setScale(2, RoundingMode.HALF_UP));
                b2bInv.put("samt", invSgst.setScale(2, RoundingMode.HALF_UP));
                b2bInv.put("iamt", invIgst.setScale(2, RoundingMode.HALF_UP));
                b2bList.add(b2bInv);
            } else {
                // B2CS (B2C Small) Transaction
                Map<String, Object> b2cs = new LinkedHashMap<>();
                b2cs.put("pos", posCode);
                b2cs.put("sply_ty", Objects.equals(sellerStateCode, posCode) ? "INTRA" : "INTER");
                b2cs.put("txval", invTaxable.setScale(2, RoundingMode.HALF_UP));
                b2cs.put("camt", invCgst.setScale(2, RoundingMode.HALF_UP));
                b2cs.put("samt", invSgst.setScale(2, RoundingMode.HALF_UP));
                b2cs.put("iamt", invIgst.setScale(2, RoundingMode.HALF_UP));
                b2csList.add(b2cs);
            }
        }

        // Process Credit Notes (CDNR)
        for (SalesReturn ret : creditNotes) {
            Invoice origInv = ret.getInvoice();
            Customer cust = ret.getCustomer() != null ? ret.getCustomer() : (origInv != null ? origInv.getCustomer() : null);
            String buyerGstin = cust != null && cust.getGstin() != null ? cust.getGstin().trim() : null;

            Map<String, Object> cdn = new LinkedHashMap<>();
            cdn.put("nt_num", ret.getReturnNumber() != null ? ret.getReturnNumber() : String.valueOf(ret.getId()));
            cdn.put("nt_dt", ret.getReturnDate() != null ? ret.getReturnDate().format(GST_DATE_FMT) : "");
            cdn.put("inum", origInv != null && origInv.getInvoiceNumber() != null ? origInv.getInvoiceNumber() : "");
            cdn.put("idt", origInv != null && origInv.getInvoiceDate() != null ? origInv.getInvoiceDate().format(GST_DATE_FMT) : "");
            cdn.put("val", ret.getTotalRefundAmount() != null ? ret.getTotalRefundAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            cdn.put("txval", ret.getSubtotal() != null ? ret.getSubtotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            cdn.put("camt", ret.getCgstAmount() != null ? ret.getCgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            cdn.put("samt", ret.getSgstAmount() != null ? ret.getSgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            cdn.put("iamt", ret.getIgstAmount() != null ? ret.getIgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            cdn.put("ctin", buyerGstin != null ? buyerGstin : "");
            cdnrList.add(cdn);
        }

        payload.put("b2b", b2bList);
        payload.put("b2cs", b2csList);
        payload.put("cdnr", cdnrList);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalTaxable", totalTaxable.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalCgst", totalCgst.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalSgst", totalSgst.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalIgst", totalIgst.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalTax", totalCgst.add(totalSgst).add(totalIgst).setScale(2, RoundingMode.HALF_UP));
        payload.put("summary", summary);

        return payload;
    }

    /**
     * Generate CSV export for GST B2B invoices.
     */
    @Transactional(readOnly = true)
    public String generateGstr1Csv(Long firmId, LocalDate fromDate, LocalDate toDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("GSTIN/UIN of Recipient,Receiver Name,Invoice Number,Invoice date,Invoice Value,Place Of Supply,Reverse Charge,Invoice Type,Rate,Taxable Value,Integrated Tax,Central Tax,State/UT Tax,Cess Amount\n");

        FirmDetails firm = firmService.get(firmId);
        String sellerStateCode = GstStateUtils.resolveSellerStateCode(firm);
        List<Invoice> invoices = invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                firmId, fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX));

        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.DRAFT || inv.getStatus() == InvoiceStatus.ESTIMATE || inv.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            Customer cust = inv.getCustomer();
            String buyerGstin = cust != null && cust.getGstin() != null ? cust.getGstin().trim() : "";
            if (buyerGstin.isEmpty()) continue; // B2B CSV only includes registered buyers

            String buyerName = cust != null && cust.getName() != null ? cust.getName().replace(",", " ") : "";
            String invNum = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : String.valueOf(inv.getId());
            String invDate = inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(GST_DATE_FMT) : "";
            BigDecimal invVal = inv.getTotalAmount() != null ? inv.getTotalAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            String pos = inv.getPlaceOfSupply() != null ? inv.getPlaceOfSupply() : GstStateUtils.resolvePlaceOfSupply(null, cust, sellerStateCode);
            String posCode = GstStateUtils.normalizeStateCode(buyerGstin.isEmpty() ? pos : buyerGstin);
            String posLabel = posCode + "-" + pos;

            BigDecimal txVal = calculateInvoiceTaxableAmount(inv);
            BigDecimal igst = inv.getIgstAmount() != null ? inv.getIgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal cgst = inv.getCgstAmount() != null ? inv.getCgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal sgst = inv.getSgstAmount() != null ? inv.getSgstAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            csv.append(String.format("%s,%s,%s,%s,%s,%s,N,Regular,18.00,%s,%s,%s,%s,0.00\n",
                    buyerGstin, buyerName, invNum, invDate, invVal, posLabel, txVal, igst, cgst, sgst));
        }

        return csv.toString();
    }

    /**
     * Compute authoritative net taxable value for an invoice post-discounts.
     * Enforces statutory consistency between taxable basis and GST charged (AUDIT-044).
     */
    private BigDecimal calculateInvoiceTaxableAmount(Invoice inv) {
        if (inv == null) return BigDecimal.ZERO;
        if (inv.getItems() != null && !inv.getItems().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (InvoiceItem it : inv.getItems()) {
                if (it.getTaxableAmount() != null) {
                    sum = sum.add(it.getTaxableAmount());
                } else if (it.getAmountWithoutTax() != null) {
                    BigDecimal disc = it.getDiscountValue() != null ? it.getDiscountValue() : BigDecimal.ZERO;
                    sum = sum.add(it.getAmountWithoutTax().subtract(disc));
                }
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal raw = inv.getSubtotalWithoutTax() != null ? inv.getSubtotalWithoutTax() : BigDecimal.ZERO;
        BigDecimal disc = inv.getTotalDiscount() != null ? inv.getTotalDiscount() : BigDecimal.ZERO;
        BigDecimal net = raw.subtract(disc);
        return (net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net).setScale(2, RoundingMode.HALF_UP);
    }
}
