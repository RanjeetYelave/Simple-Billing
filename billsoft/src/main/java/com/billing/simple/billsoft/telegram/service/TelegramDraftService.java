package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.*;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import com.billing.simple.billsoft.telegram.entities.TelegramDraftItem;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftItemRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TelegramDraftService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDraftService.class);

    private final TelegramDraftRepository draftRepo;
    private final TelegramDraftItemRepository itemRepo;
    private final TelegramDocumentDeliveryRepository deliveryRepo;
    private final InvoiceService invoiceService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final PurchaseOrderService poService;
    private final PartyRepository partyRepository;
    private final ExpenseService expenseService;
    private final SavingService savingService;
    private final LeaveRecordRepository leaveRecordRepository;
    private final EmployeeAdvanceRepository employeeAdvanceRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final EmployeeRepository employeeRepository;

    public TelegramDraftService(TelegramDraftRepository draftRepo,
            TelegramDraftItemRepository itemRepo,
            TelegramDocumentDeliveryRepository deliveryRepo,
            InvoiceService invoiceService,
            CustomerService customerService,
            ProductService productService,
            PurchaseOrderService poService,
            PartyRepository partyRepository,
            ExpenseService expenseService,
            SavingService savingService,
            LeaveRecordRepository leaveRecordRepository,
            EmployeeAdvanceRepository employeeAdvanceRepository,
            SalaryRecordRepository salaryRecordRepository,
            EmployeeRepository employeeRepository) {
        this.draftRepo = draftRepo;
        this.itemRepo = itemRepo;
        this.deliveryRepo = deliveryRepo;
        this.invoiceService = invoiceService;
        this.customerService = customerService;
        this.productService = productService;
        this.poService = poService;
        this.partyRepository = partyRepository;
        this.expenseService = expenseService;
        this.savingService = savingService;
        this.leaveRecordRepository = leaveRecordRepository;
        this.employeeAdvanceRepository = employeeAdvanceRepository;
        this.salaryRecordRepository = salaryRecordRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public TelegramDraft startDraft(Long chatId, Long userId, Long firmId, String documentType) {
        // Cancel any active draft for this chat
        draftRepo.findByChatIdAndDraftStateNot(chatId, "EXECUTED").ifPresent(d -> {
            if ("BUILDING".equals(d.getDraftState()) || "PREVIEW".equals(d.getDraftState())) {
                d.setDraftState("CANCELLED");
                draftRepo.save(d);
            }
        });

        TelegramDraft draft = new TelegramDraft();
        draft.setChatId(chatId);
        draft.setUserId(userId);
        draft.setFirmId(firmId);
        draft.setDocumentType(documentType != null ? documentType : "INVOICE");
        draft.setIdempotencyKey(UUID.randomUUID().toString());
        draft.setDraftState("BUILDING");
        draft.setVersion(1);
        draft.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        return draftRepo.save(draft);
    }

    public Optional<TelegramDraft> getDraft(Long draftId) {
        return draftRepo.findById(draftId);
    }

    public List<TelegramDraftItem> getDraftItems(Long draftId) {
        return itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
    }

    @Transactional
    public void setCustomer(Long draftId, Long customerId, String customerName, String customerPhone) {
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setCustomerId(customerId);
            d.setCustomerName(customerName);
            d.setCustomerPhone(customerPhone);
            d.setVersion(d.getVersion() + 1);
            d.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(d);
        });
    }

    @Transactional
    public TelegramDraftItem addItem(Long draftId, Long productId, String itemName, String variantDetails,
            String unit, BigDecimal quantity, BigDecimal unitRate, BigDecimal taxRate) {
        TelegramDraft draft = draftRepo.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        List<TelegramDraftItem> existingItems = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        TelegramDraftItem item = new TelegramDraftItem();
        item.setDraftId(draftId);
        item.setItemOrder(existingItems.size() + 1);
        item.setProductId(productId);
        item.setItemName(itemName != null ? itemName : "Item");
        item.setVariantDetails(variantDetails);
        item.setUnit(unit != null ? unit : "Piece");
        item.setQuantity(quantity != null ? quantity : BigDecimal.ONE);
        item.setUnitRate(unitRate != null ? unitRate : BigDecimal.ZERO);
        item.setTaxRate(taxRate != null ? taxRate : BigDecimal.ZERO);

        BigDecimal lineSubtotal = item.getQuantity().multiply(item.getUnitRate());
        BigDecimal taxAmount = lineSubtotal.multiply(item.getTaxRate()).divide(BigDecimal.valueOf(100), 2,
                java.math.RoundingMode.HALF_UP);
        item.setTaxAmount(taxAmount);
        item.setLineTotal(lineSubtotal.add(taxAmount));

        TelegramDraftItem savedItem = itemRepo.save(item);

        draft.setVersion(draft.getVersion() + 1);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.save(draft);

        return savedItem;
    }

    @Transactional
    public void removeItem(Long draftId, Long itemId) {
        itemRepo.deleteById(itemId);
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setVersion(d.getVersion() + 1);
            d.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(d);
        });
    }

    @Transactional
    public void cancelDraft(Long draftId) {
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setDraftState("CANCELLED");
            d.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(d);
        });
    }

    @Transactional
    public Invoice previewDraft(Long draftId) {
        TelegramDraft draft = draftRepo.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        List<TelegramDraftItem> items = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot preview draft with no items.");
        }

        InvoiceRequest request = buildInvoiceRequest(draft, items);

        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(draft.getFirmId());
            Invoice preview = invoiceService.previewInvoice(request);

            draft.setSubtotal(preview.getSubtotalWithoutTax());
            draft.setTaxAmount(preview.getTotalTax());
            draft.setTotalAmount(preview.getTotalAmount());
            draft.setDraftState("PREVIEW");
            draft.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            draft.setVersion(draft.getVersion() + 1);
            draft.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(draft);

            return preview;
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    @Transactional
    public PurchaseOrder previewPoDraft(Long draftId) {
        TelegramDraft draft = draftRepo.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        List<TelegramDraftItem> items = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot preview PO draft with no items.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (TelegramDraftItem item : items) {
            BigDecimal lineSub = item.getQuantity().multiply(item.getUnitRate());
            BigDecimal lineTax = lineSub.multiply(item.getTaxRate()).divide(BigDecimal.valueOf(100), 2,
                    RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineSub);
            taxTotal = taxTotal.add(lineTax);
        }
        BigDecimal total = subtotal.add(taxTotal);

        draft.setSubtotal(subtotal);
        draft.setTaxAmount(taxTotal);
        draft.setTotalAmount(total);
        draft.setDraftState("PREVIEW");
        draft.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        draft.setVersion(draft.getVersion() + 1);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.save(draft);

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(draft.getFirmId());
        po.setPartyName(draft.getCustomerName());
        po.setTotalAmount(total);
        return po;
    }

    @Transactional(rollbackFor = Exception.class)
    public Invoice executeDraft(Long draftId, Integer expectedVersion, String installationId) {
        // Pessimistic lock for atomic execution claim
        TelegramDraft draft = draftRepo.findByIdForUpdate(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        // Idempotency: if already executed, load and return
        if ("EXECUTED".equals(draft.getDraftState()) && draft.getCreatedDocumentId() != null) {
            log.info("Draft {} already executed. Returning existing document ID {}.", draftId,
                    draft.getCreatedDocumentId());
            return invoiceService.getById(draft.getCreatedDocumentId());
        }

        if (!"PREVIEW".equals(draft.getDraftState())) {
            throw new IllegalStateException("Draft cannot be executed in state: " + draft.getDraftState());
        }

        if (!draft.getVersion().equals(expectedVersion)) {
            throw new IllegalStateException("Draft version mismatch (expected " + expectedVersion + ", current "
                    + draft.getVersion() + "). Please preview again.");
        }

        if (draft.getExpiresAt().isBefore(LocalDateTime.now())) {
            draft.setDraftState("EXPIRED");
            draftRepo.save(draft);
            throw new IllegalStateException("Draft preview has expired. Please preview and confirm again.");
        }

        List<TelegramDraftItem> items = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot execute draft with zero items.");
        }

        InvoiceRequest request = buildInvoiceRequest(draft, items);
        request.setIdempotencyKey(draft.getIdempotencyKey());

        Long prev = TenantContext.getCurrentFirmId();
        Invoice saved;
        try {
            TenantContext.setCurrentFirmId(draft.getFirmId());
            if ("ESTIMATE".equalsIgnoreCase(draft.getDocumentType())) {
                saved = invoiceService.createEstimate(request);
            } else {
                saved = invoiceService.createInvoice(request);
            }
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }

        String docNumber = saved.getInvoiceNumber() != null ? saved.getInvoiceNumber() : saved.getEstimateNumber();
        draft.setDraftState("EXECUTED");
        draft.setCreatedDocumentId(saved.getId());
        draft.setCreatedDocumentNumber(docNumber);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.save(draft);

        // Transactional outbox: persist delivery intent in the same transaction
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId(installationId != null ? installationId : "LOCAL");
        delivery.setUserId(draft.getUserId());
        delivery.setChatId(draft.getChatId());
        delivery.setFirmId(draft.getFirmId());
        delivery.setDocumentType("ESTIMATE".equalsIgnoreCase(draft.getDocumentType()) ? "ESTIMATE_PDF" : "INVOICE_PDF");
        delivery.setDocumentId(saved.getId());
        delivery.setDraftId(draft.getDraftId());
        delivery.setDocumentNumber(docNumber);
        delivery.setOperationType("CREATE");
        delivery.setRecipientName(draft.getCustomerName());
        delivery.setRecipientPhone(draft.getCustomerPhone());
        delivery.setDeliveryState("QUEUED");
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        deliveryRepo.save(delivery);

        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder executePoDraft(Long draftId, Integer expectedVersion, String installationId) {
        TelegramDraft draft = draftRepo.findByIdForUpdate(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if ("EXECUTED".equals(draft.getDraftState()) && draft.getCreatedDocumentId() != null) {
            return poService.getPurchaseOrderById(draft.getCreatedDocumentId(), draft.getFirmId()).orElse(null);
        }

        if (!"PREVIEW".equals(draft.getDraftState())) {
            throw new IllegalStateException("PO Draft cannot be executed in state: " + draft.getDraftState());
        }

        if (!draft.getVersion().equals(expectedVersion)) {
            throw new IllegalStateException("Draft version mismatch. Please preview again.");
        }

        List<TelegramDraftItem> items = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot execute PO draft with zero items.");
        }

        Party party = partyRepository.findByIdAndFirmId(draft.getCustomerId(), draft.getFirmId())
                .orElseThrow(() -> new IllegalArgumentException("Party not found: " + draft.getCustomerId()));

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(draft.getFirmId());
        po.setParty(party);
        po.setPoDate(LocalDate.now());
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setPaymentStatus("YET_TO_PAY");

        List<PurchaseOrderItem> poItems = new ArrayList<>();
        for (TelegramDraftItem di : items) {
            PurchaseOrderItem poi = new PurchaseOrderItem();
            poi.setPurchaseOrder(po);
            poi.setProductId(di.getProductId());
            poi.setProductName(di.getItemName());
            poi.setQuantity(di.getQuantity());
            poi.setUnit(di.getUnit());
            poi.setUnitPrice(di.getUnitRate());
            poi.setGstPercent(di.getTaxRate());
            poi.setTotalAmount(di.getLineTotal());
            poItems.add(poi);
        }
        po.setItems(poItems);

        Long prev = TenantContext.getCurrentFirmId();
        PurchaseOrder savedPo;
        try {
            TenantContext.setCurrentFirmId(draft.getFirmId());
            savedPo = poService.createPurchaseOrder(po);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }

        draft.setDraftState("EXECUTED");
        draft.setCreatedDocumentId(savedPo.getId());
        draft.setCreatedDocumentNumber(savedPo.getPoNumber());
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.save(draft);

        // Queue PO delivery
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId(installationId != null ? installationId : "LOCAL");
        delivery.setUserId(draft.getUserId());
        delivery.setChatId(draft.getChatId());
        delivery.setFirmId(draft.getFirmId());
        delivery.setDocumentType("PO_PDF");
        delivery.setDocumentId(savedPo.getId());
        delivery.setDraftId(draft.getDraftId());
        delivery.setDocumentNumber(savedPo.getPoNumber());
        delivery.setOperationType("CREATE");
        delivery.setRecipientName(party != null ? party.getName() : savedPo.getPartyName());
        delivery.setRecipientPhone(party != null ? party.getPhone() : null);
        delivery.setDeliveryState("QUEUED");
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        deliveryRepo.save(delivery);

        return savedPo;
    }

    @Transactional
    public Expense recordExpense(Long firmId, BigDecimal amount, String title, String category) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            Expense exp = Expense.builder()
                    .firmId(firmId)
                    .amount(amount)
                    .title(title != null ? title : "Expense")
                    .category(category != null ? category : "General")
                    .expenseDate(LocalDate.now())
                    .paymentMode("Cash")
                    .build();
            return expenseService.createExpense(exp);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    @Transactional
    public SavingRecord recordSaving(Long firmId, BigDecimal amount, String title) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            SavingRecord rec = SavingRecord.builder()
                    .firmId(firmId)
                    .amount(amount)
                    .title(title != null ? title : "Savings Deposit")
                    .category("Business Reserves")
                    .savingDate(LocalDate.now())
                    .paymentMode("Bank Transfer")
                    .build();
            return savingService.createSaving(rec);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    @Transactional
    public LeaveRecord recordLeave(Long firmId, Long employeeId, LocalDate start, LocalDate end, String leaveType,
            String reason) {
        Employee emp = employeeRepository.findByIdAndFirmId(employeeId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        LeaveRecord lr = new LeaveRecord();
        lr.setEmployee(emp);
        lr.setStartDate(start != null ? start : LocalDate.now());
        lr.setEndDate(end != null ? end : lr.getStartDate());
        lr.setType(leaveType != null ? leaveType : "CASUAL");
        lr.setTotalDays(
                Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(lr.getStartDate(), lr.getEndDate()) + 1));
        lr.setReason(reason != null ? reason : "Leave request via Telegram");
        lr.setStatus("APPROVED");
        return leaveRecordRepository.save(lr);
    }

    @Transactional
    public EmployeeAdvance recordAdvance(Long firmId, Long employeeId, Double amount, String reason) {
        Employee emp = employeeRepository.findByIdAndFirmId(employeeId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        EmployeeAdvance adv = new EmployeeAdvance();
        adv.setEmployee(emp);
        adv.setDate(LocalDate.now());
        adv.setAmount(amount);
        adv.setDescription(reason != null ? reason : "Salary Advance via Telegram");
        EmployeeAdvance saved = employeeAdvanceRepository.save(adv);

        double cur = emp.getCurrentAdvanceBalance() != null ? emp.getCurrentAdvanceBalance() : 0.0;
        emp.setCurrentAdvanceBalance(cur + amount);
        employeeRepository.save(emp);

        return saved;
    }

    @Transactional
    public SalaryRecord processSalary(Long firmId, Long employeeId, String monthYear, Double advanceDeduct,
            String installationId, Long userId, Long chatId) {
        return processSalary(firmId, employeeId, monthYear, advanceDeduct, installationId, userId, chatId, "INDIVIDUAL");
    }

    @Transactional
    public SalaryRecord processSalary(Long firmId, Long employeeId, String monthYear, Double advanceDeduct, String installationId, Long userId, Long chatId, String operationType) {
        Employee emp = employeeRepository.findByIdAndFirmId(employeeId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        Optional<SalaryRecord> existing = salaryRecordRepository.findByEmployeeIdAndMonthYear(employeeId, monthYear);
        if (existing.isPresent()) {
            throw new IllegalStateException(
                    "Salary for " + monthYear + " has already been processed for " + emp.getName());
        }

        double curAdv = emp.getCurrentAdvanceBalance() != null ? emp.getCurrentAdvanceBalance() : 0.0;
        double deduct = advanceDeduct != null ? advanceDeduct : 0.0;
        if (deduct < 0) {
            throw new IllegalArgumentException("Advance deduction cannot be negative");
        }
        if (deduct > curAdv) {
            throw new IllegalArgumentException(
                    "Advance deduction (₹" + deduct + ") exceeds current advance balance (₹" + curAdv + ")");
        }

        if (deduct > 0) {
            EmployeeAdvance dec = new EmployeeAdvance();
            dec.setEmployee(emp);
            dec.setDate(LocalDate.now());
            dec.setAmount(-deduct);
            dec.setDescription("Salary Deduction for " + monthYear);
            employeeAdvanceRepository.save(dec);

            emp.setCurrentAdvanceBalance(curAdv - deduct);
            employeeRepository.save(emp);
        }

        double baseSal = emp.getMonthlyBaseSalary() != null ? emp.getMonthlyBaseSalary() : 0.0;
        double netPaid = Math.max(0.0, baseSal - deduct);

        SalaryRecord sal = new SalaryRecord();
        sal.setEmployee(emp);
        sal.setMonthYear(monthYear);
        sal.setPaymentDate(LocalDate.now());
        sal.setBaseSalaryAtTime(baseSal);
        sal.setAdvanceDeducted(deduct);
        sal.setNetPaid(netPaid);
        SalaryRecord saved = salaryRecordRepository.save(sal);

        // Queue payslip delivery
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId(installationId != null ? installationId : "LOCAL");
        delivery.setUserId(userId);
        delivery.setChatId(chatId);
        delivery.setFirmId(firmId);
        delivery.setDocumentType("PAYSLIP_PDF");
        delivery.setDocumentId(saved.getId());
        delivery.setDocumentNumber(saved.getMonthYear());
        delivery.setOperationType(operationType != null ? operationType : "INDIVIDUAL");
        delivery.setRecipientName(emp.getName());
        delivery.setRecipientPhone(emp.getPhone());
        delivery.setDeliveryState("QUEUED");
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        deliveryRepo.save(delivery);

        return saved;
    }

    private InvoiceRequest buildInvoiceRequest(TelegramDraft draft, List<TelegramDraftItem> items) {
        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(draft.getFirmId());
        request.setCustomerId(draft.getCustomerId());
        request.setCustomerName(draft.getCustomerName());
        request.setCustomerPhone(draft.getCustomerPhone());

        if ("ESTIMATE".equalsIgnoreCase(draft.getDocumentType())) {
            request.setStatus(InvoiceStatus.ESTIMATE);
        } else {
            request.setStatus(InvoiceStatus.UNPAID);
        }

        List<InvoiceRequestItem> reqItems = new ArrayList<>();
        for (TelegramDraftItem di : items) {
            InvoiceRequestItem ri = new InvoiceRequestItem();
            ri.setProductId(di.getProductId());
            ri.setProductName(di.getItemName());
            ri.setQty(di.getQuantity() != null ? di.getQuantity().intValue() : 1);
            ri.setUnit(di.getUnit());
            ri.setPricePerUnit(di.getUnitRate());
            ri.setGstPercent(di.getTaxRate());
            reqItems.add(ri);
        }
        request.setItems(reqItems);
        return request;
    }
}
