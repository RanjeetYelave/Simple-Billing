package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.*;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class TelegramReadService {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final PurchaseOrderService poService;
    private final PurchaseOrderRepository poRepository;
    private final DebtorService debtorService;
    private final FirmDetailsService firmService;
    private final PartyRepository partyRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRecordRepository leaveRecordRepository;
    private final EmployeeAdvanceRepository employeeAdvanceRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final StockMovementRepository stockMovementRepository;

    public TelegramReadService(InvoiceService invoiceService,
                               InvoiceRepository invoiceRepository,
                               CustomerService customerService,
                               ProductService productService,
                               PurchaseOrderService poService,
                               PurchaseOrderRepository poRepository,
                               DebtorService debtorService,
                               FirmDetailsService firmService,
                               PartyRepository partyRepository,
                               EmployeeRepository employeeRepository,
                               LeaveRecordRepository leaveRecordRepository,
                               EmployeeAdvanceRepository employeeAdvanceRepository,
                               SalaryRecordRepository salaryRecordRepository,
                               StockMovementRepository stockMovementRepository) {
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.customerService = customerService;
        this.productService = productService;
        this.poService = poService;
        this.poRepository = poRepository;
        this.debtorService = debtorService;
        this.firmService = firmService;
        this.partyRepository = partyRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRecordRepository = leaveRecordRepository;
        this.employeeAdvanceRepository = employeeAdvanceRepository;
        this.salaryRecordRepository = salaryRecordRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    public List<FirmDetails> getActiveFirms() {
        return firmService.list();
    }

    public Optional<FirmDetails> getFirm(Long firmId) {
        return Optional.ofNullable(firmService.get(firmId));
    }

    public FirmAnalyticsResponse getDashboard(Long firmId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return invoiceService.getFirmAnalytics(firmId);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public List<Customer> searchCustomers(Long firmId, String query) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return customerService.search(firmId, query);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Optional<Customer> getCustomerById(Long firmId, Long customerId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return Optional.ofNullable(customerService.getById(customerId, firmId));
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public List<Product> searchProducts(Long firmId, String query) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return productService.searchProducts(firmId, query);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Optional<Product> getProductById(Long firmId, Long productId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return Optional.ofNullable(productService.getById(productId, firmId));
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Page<Invoice> getRecentInvoices(Long firmId, int limit) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return invoiceService.getPaginated(firmId, PageRequest.of(0, limit));
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Page<Invoice> getRecentEstimates(Long firmId, int limit) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return invoiceService.getPaginatedEstimates(firmId, PageRequest.of(0, limit));
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Optional<Invoice> getInvoiceById(Long firmId, Long invoiceId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            Invoice inv = invoiceService.getById(invoiceId);
            if (inv != null && firmId.equals(inv.getFirmId())) {
                return Optional.of(inv);
            }
            return Optional.empty();
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Object getDebtorsSummary(Long firmId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return debtorService.getOverview(firmId);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public Optional<PurchaseOrder> getPurchaseOrderById(Long firmId, Long poId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return poService.getPurchaseOrderById(poId, firmId);
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public List<PurchaseOrder> getRecentPurchaseOrders(Long firmId, int limit) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return poService.getPaginatedPurchaseOrders(firmId, PageRequest.of(0, limit)).getContent();
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    public List<Party> searchParties(Long firmId, String query) {
        if (query == null || query.isBlank()) {
            return partyRepository.findByFirmIdOrderByNameAsc(firmId);
        }
        return partyRepository.searchByFirmIdAndQuery(firmId, query.trim());
    }

    public Optional<Party> getPartyById(Long firmId, Long partyId) {
        return partyRepository.findByIdAndFirmId(partyId, firmId);
    }

    public List<Employee> getEmployees(Long firmId) {
        return employeeRepository.findByFirmIdOrderByNameAsc(firmId);
    }

    public List<Employee> searchEmployees(Long firmId, String query) {
        if (query == null || query.isBlank()) {
            return employeeRepository.findByFirmIdOrderByNameAsc(firmId);
        }
        return employeeRepository.searchByFirmIdAndQuery(firmId, query.trim());
    }

    public Page<Invoice> getCustomerInvoices(Long firmId, Long customerId, LocalDate from, LocalDate to, int page, int size) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            java.time.LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
            java.time.LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
            return invoiceRepository.findCustomerInvoices(firmId, customerId, fromDt, toDt, PageRequest.of(page, size));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Page<Invoice> getCustomerEstimates(Long firmId, Long customerId, LocalDate from, LocalDate to, int page, int size) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            java.time.LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
            java.time.LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
            return invoiceRepository.findCustomerEstimates(firmId, customerId, fromDt, toDt, PageRequest.of(page, size));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Page<PurchaseOrder> getPartyPurchaseOrders(Long firmId, Long partyId, LocalDate from, LocalDate to, int page, int size) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return poRepository.findPartyPurchaseOrders(firmId, partyId, from, to, PageRequest.of(page, size));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Optional<Employee> getEmployeeById(Long firmId, Long employeeId) {
        return employeeRepository.findByIdAndFirmId(employeeId, firmId);
    }

    public List<LeaveRecord> getEmployeeLeaves(Long employeeId) {
        return leaveRecordRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    public List<EmployeeAdvance> getEmployeeAdvances(Long employeeId) {
        return employeeAdvanceRepository.findByEmployeeIdOrderByDateDesc(employeeId);
    }

    public List<SalaryRecord> getEmployeeSalaries(Long employeeId) {
        return salaryRecordRepository.findByEmployeeIdOrderByPaymentDateDesc(employeeId);
    }

    public List<StockMovement> getRecentStockMovements(Long firmId, int limit) {
        return stockMovementRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
    }

    public byte[] generateStockMovementPdf(Long firmId) throws Exception {
        return generateStockMovementPdf(firmId, null, null);
    }

    public byte[] generateStockMovementPdf(Long firmId, LocalDate from, LocalDate to) throws Exception {
        FirmDetails firm = firmService.get(firmId);
        String firmName = (firm != null && firm.getFirmName() != null) ? firm.getFirmName() : "RupeeCRM";
        List<StockMovement> allMovements = stockMovementRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
        List<StockMovement> movements = new java.util.ArrayList<>();
        java.time.LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        java.time.LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
        for (StockMovement sm : allMovements) {
            if (sm.getCreatedAt() != null) {
                if (fromDt != null && sm.getCreatedAt().isBefore(fromDt)) continue;
                if (toDt != null && sm.getCreatedAt().isAfter(toDt)) continue;
            }
            movements.add(sm);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 28, 28, 28, 38);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(15, 23, 42));
        Font subFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 116, 139));
        Font headerFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
        Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(30, 41, 59));

        Paragraph title = new Paragraph("Stock Movement Statement", titleFont);
        String periodSubtitle = (from != null && to != null) ?
                ("Period: " + from.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + " to " + to.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))) :
                ("Generated on: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        Paragraph subtitle = new Paragraph("Firm: " + firmName + "  |  " + periodSubtitle, subFont);
        subtitle.setSpacingAfter(15);
        doc.add(title);
        doc.add(subtitle);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 2.2f, 3.5f, 1.8f, 1.5f, 1.5f, 2.5f });

        String[] headers = { "Date / Time", "Product", "Movement", "Change", "New Stock", "Reference / Note" };
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Paragraph(h, headerFont));
            c.setBackgroundColor(new Color(15, 23, 42));
            c.setPadding(6);
            table.addCell(c);
        }

        int count = 0;
        for (StockMovement sm : movements) {
            if (count++ >= 60)
                break; // Limit to 60 recent movements in PDF
            String dateStr = sm.getCreatedAt() != null
                    ? sm.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MM-yy HH:mm"))
                    : "—";
            String prodName = sm.getProductName() != null ? sm.getProductName() : ("Product #" + sm.getProductId());
            String moveType = sm.getMovementType() != null ? sm.getMovementType() : "ADJUSTMENT";
            String changeStr = sm.getQuantityChange() != null ? sm.getQuantityChange().toString() : "0";
            String newStockStr = sm.getNewStock() != null ? sm.getNewStock().toString() : "0";
            String noteStr = (sm.getReferenceType() != null ? (sm.getReferenceType() + " ") : "")
                    + (sm.getNote() != null ? sm.getNote() : "");

            table.addCell(createCell(dateStr, cellFont));
            table.addCell(createCell(prodName, cellFont));
            table.addCell(createCell(moveType, cellFont));
            table.addCell(createCell(changeStr, cellFont));
            table.addCell(createCell(newStockStr, cellFont));
            table.addCell(createCell(noteStr, cellFont));
        }

        if (movements.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Paragraph("No stock movements recorded.", cellFont));
            empty.setColspan(6);
            empty.setPadding(12);
            empty.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            table.addCell(empty);
        }

        doc.add(table);
        doc.close();
        return baos.toByteArray();
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Paragraph(text != null ? text : "", font));
        c.setPadding(5);
        c.setBorderColor(new Color(226, 232, 240));
        return c;
    }
}
