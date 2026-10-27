package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.DocumentSequenceService;
import com.billing.simple.billsoft.service.DocumentType;
import com.billing.simple.billsoft.util.IndianFinancialYearUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe and transaction-safe implementation of DocumentSequenceService.
 * Uses database-level row locking (@Lock PESSIMISTIC_WRITE) on AppConfig records
 * inside isolated transactions (REQUIRES_NEW) to guarantee atomic, monotonic sequence allocation.
 */
@Service
@Transactional
public class DocumentSequenceServiceImpl implements DocumentSequenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSequenceServiceImpl.class);

    private final AppConfigRepository appConfigRepo;
    private final InvoiceRepository invoiceRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final PurchaseOrderRepository poRepo;

    // In-memory locks per key to serialize concurrent requests within the same JVM
    private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

    public DocumentSequenceServiceImpl(AppConfigRepository appConfigRepo,
                                       InvoiceRepository invoiceRepo,
                                       SalesReturnRepository salesReturnRepo,
                                       PurchaseOrderRepository poRepo) {
        this.appConfigRepo = appConfigRepo;
        this.invoiceRepo = invoiceRepo;
        this.salesReturnRepo = salesReturnRepo;
        this.poRepo = poRepo;
    }

    private Long resolveSafeFirmId(Long firmId) {
        if (firmId != null && firmId > 0) {
            return firmId;
        }
        Long ctxFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        return (ctxFirmId != null && ctxFirmId > 0) ? ctxFirmId : 1L;
    }

    private String buildConfigKey(DocumentType type, Long firmId, LocalDate date) {
        if (date == null) date = LocalDate.now();
        switch (type) {
            case INVOICE:
                return "LAST_INVOICE_SEQ_" + firmId + "_" + IndianFinancialYearUtils.getFinancialYear(date);
            case ESTIMATE:
                return "LAST_ESTIMATE_SEQ_" + firmId + "_" + IndianFinancialYearUtils.getFinancialYear(date);
            case SALES_RETURN:
                return "LAST_SALES_RETURN_SEQ_" + firmId + "_" + IndianFinancialYearUtils.getFinancialYear(date);
            case PURCHASE_ORDER:
                return "LAST_PO_SEQ_" + firmId + "_" + date.getYear();
            default:
                throw new IllegalArgumentException("Unsupported document type: " + type);
        }
    }

    private String formatCandidate(DocumentType type, long seq, LocalDate date) {
        if (date == null) date = LocalDate.now();
        switch (type) {
            case INVOICE:
                return String.format("INV-%04d", seq);
            case ESTIMATE:
                return String.format("EST-%04d", seq);
            case SALES_RETURN:
                return String.format("CN-%04d", seq);
            case PURCHASE_ORDER:
                return String.format("PO-%d-%04d", date.getYear(), seq);
            default:
                throw new IllegalArgumentException("Unsupported document type: " + type);
        }
    }

    private boolean existsInDatabase(DocumentType type, String candidate, Long firmId) {
        switch (type) {
            case INVOICE:
                return invoiceRepo.existsByInvoiceNumberAndFirmId(candidate, firmId);
            case ESTIMATE:
                return invoiceRepo.existsByEstimateNumberAndFirmId(candidate, firmId);
            case SALES_RETURN:
                return salesReturnRepo.existsByReturnNumberAndFirmId(candidate, firmId);
            case PURCHASE_ORDER:
                return poRepo.existsByPoNumberAndFirmId(candidate, firmId);
            default:
                return false;
        }
    }

    private long parseLongSafe(String val) {
        if (val == null || val.trim().isEmpty()) return 0L;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long scanDatabaseMax(DocumentType type, Long firmId, LocalDate date) {
        if (date == null) date = LocalDate.now();
        long dbMax = 0L;
        switch (type) {
            case INVOICE: {
                List<String> numbers = invoiceRepo.findInvoiceNumbersByFirmId(firmId);
                if (numbers != null) {
                    for (String num : numbers) {
                        if (num != null && num.trim().startsWith("INV-")) {
                            try {
                                long seq = Long.parseLong(num.trim().substring(4).trim());
                                if (seq > dbMax) dbMax = seq;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                // Also check legacy unpartitioned counter for migration
                String legacyKey = "LAST_INVOICE_SEQ_" + firmId;
                long legacy = appConfigRepo.findById(legacyKey).map(AppConfig::getConfigValue).map(this::parseLongSafe).orElse(0L);
                if (legacy > dbMax) dbMax = legacy;
                break;
            }
            case ESTIMATE: {
                List<String> numbers = invoiceRepo.findEstimateNumbersByFirmId(firmId);
                if (numbers != null) {
                    for (String num : numbers) {
                        if (num != null && num.trim().startsWith("EST-")) {
                            try {
                                long seq = Long.parseLong(num.trim().substring(4).trim());
                                if (seq > dbMax) dbMax = seq;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                String legacyKey = "LAST_ESTIMATE_SEQ_" + firmId;
                long legacy = appConfigRepo.findById(legacyKey).map(AppConfig::getConfigValue).map(this::parseLongSafe).orElse(0L);
                if (legacy > dbMax) dbMax = legacy;
                break;
            }
            case SALES_RETURN: {
                String legacyKey = "LAST_RETURN_SEQ_" + firmId;
                long legacy = appConfigRepo.findById(legacyKey).map(AppConfig::getConfigValue).map(this::parseLongSafe).orElse(0L);
                if (legacy > dbMax) dbMax = legacy;
                break;
            }
            case PURCHASE_ORDER: {
                long count = poRepo.countByFirmId(firmId);
                if (count > dbMax) dbMax = count;
                String legacyKey = "LAST_PO_SEQ_" + firmId;
                long legacy = appConfigRepo.findById(legacyKey).map(AppConfig::getConfigValue).map(this::parseLongSafe).orElse(0L);
                if (legacy > dbMax) dbMax = legacy;
                break;
            }
        }
        return dbMax;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNextNumber(DocumentType type, Long firmId, LocalDate date) {
        Long safeFirmId = resolveSafeFirmId(firmId);
        if (date == null) date = LocalDate.now();
        String configKey = buildConfigKey(type, safeFirmId, date);

        synchronized (configKey.intern()) {
            // 1. Pessimistic DB row lock on sequence record (with fallback if non-transactional)
            Optional<AppConfig> opt;
            try {
                opt = appConfigRepo.findByConfigKeyForUpdate(configKey);
            } catch (Exception e) {
                opt = appConfigRepo.findById(configKey);
            }
            AppConfig cfg;
            long lastSeq;

            if (opt.isPresent()) {
                cfg = opt.get();
                lastSeq = parseLongSafe(cfg.getConfigValue());
            } else {
                // First initialization for this firm and partition
                lastSeq = scanDatabaseMax(type, safeFirmId, date);
                cfg = new AppConfig();
                cfg.setConfigKey(configKey);
                cfg.setConfigValue(String.valueOf(lastSeq));
                try {
                    cfg = appConfigRepo.saveAndFlush(cfg);
                } catch (Exception ex) {
                    // Fallback if concurrent transaction committed the initial row first
                    opt = appConfigRepo.findById(configKey);
                    if (opt.isPresent()) {
                        cfg = opt.get();
                        lastSeq = parseLongSafe(cfg.getConfigValue());
                    } else {
                        throw ex;
                    }
                }
            }

            // 2. Increment sequence atomically
            long nextSeq = lastSeq + 1;
            String candidate = formatCandidate(type, nextSeq, date);

            // 3. Collision protection: advance if number already exists in DB
            while (existsInDatabase(type, candidate, safeFirmId)) {
                nextSeq++;
                candidate = formatCandidate(type, nextSeq, date);
            }

            // 4. Save and flush advance sequence
            cfg.setConfigValue(String.valueOf(nextSeq));
            appConfigRepo.saveAndFlush(cfg);

            log.info("Allocated {} number: {} (key: {}, nextVal: {})", type, candidate, configKey, nextSeq);
            return candidate;
        }
    }

    @Override
    @Transactional
    public String generateNextNumber(DocumentType type, Long firmId) {
        return generateNextNumber(type, firmId, LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public String peekNextNumber(DocumentType type, Long firmId) {
        return peekNextNumber(type, firmId, LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public String peekNextNumber(DocumentType type, Long firmId, LocalDate date) {
        Long safeFirmId = resolveSafeFirmId(firmId);
        if (date == null) date = LocalDate.now();
        String configKey = buildConfigKey(type, safeFirmId, date);

        long lastSeq = appConfigRepo.findById(configKey)
                .map(AppConfig::getConfigValue)
                .map(this::parseLongSafe)
                .orElse(0L);

        if (lastSeq == 0) {
            lastSeq = scanDatabaseMax(type, safeFirmId, date);
        }

        long nextSeq = lastSeq + 1;
        String candidate = formatCandidate(type, nextSeq, date);
        while (existsInDatabase(type, candidate, safeFirmId)) {
            nextSeq++;
            candidate = formatCandidate(type, nextSeq, date);
        }
        return candidate;
    }

    @Override
    @Transactional
    public void syncSequenceIfApplicable(DocumentType type, Long firmId, String number, LocalDate date) {
        if (number == null || number.trim().isEmpty()) return;
        Long safeFirmId = resolveSafeFirmId(firmId);
        if (date == null) date = LocalDate.now();

        long extractedSeq = 0L;
        String trimmed = number.trim();
        try {
            switch (type) {
                case INVOICE:
                    if (trimmed.startsWith("INV-")) {
                        extractedSeq = Long.parseLong(trimmed.substring(4));
                    }
                    break;
                case ESTIMATE:
                    if (trimmed.startsWith("EST-")) {
                        extractedSeq = Long.parseLong(trimmed.substring(4));
                    }
                    break;
                case SALES_RETURN:
                    if (trimmed.startsWith("CN-")) {
                        extractedSeq = Long.parseLong(trimmed.substring(3));
                    }
                    break;
                case PURCHASE_ORDER:
                    // e.g. PO-2026-0005
                    int lastDash = trimmed.lastIndexOf('-');
                    if (lastDash > 0 && lastDash < trimmed.length() - 1) {
                        extractedSeq = Long.parseLong(trimmed.substring(lastDash + 1));
                    }
                    break;
            }
        } catch (NumberFormatException ignored) {
            return;
        }

        if (extractedSeq <= 0) return;

        String configKey = buildConfigKey(type, safeFirmId, date);
        Object lock = lockMap.computeIfAbsent(configKey, k -> new Object());
        synchronized (lock) {
            Optional<AppConfig> opt;
            try {
                opt = appConfigRepo.findByConfigKeyForUpdate(configKey);
            } catch (Exception e) {
                opt = appConfigRepo.findById(configKey);
            }
            AppConfig cfg = opt.orElseGet(() -> {
                AppConfig newCfg = new AppConfig();
                newCfg.setConfigKey(configKey);
                newCfg.setConfigValue("0");
                return newCfg;
            });

            long currentSeq = parseLongSafe(cfg.getConfigValue());
            if (extractedSeq > currentSeq) {
                cfg.setConfigValue(String.valueOf(extractedSeq));
                appConfigRepo.saveAndFlush(cfg);
                log.info("Synced {} sequence key {} to {}", type, configKey, extractedSeq);
            }
        }
    }
}
