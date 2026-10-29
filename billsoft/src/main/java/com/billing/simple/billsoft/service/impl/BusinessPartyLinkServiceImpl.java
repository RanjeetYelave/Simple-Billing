package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.BusinessPartyLink;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.BusinessPartyLinkService;
import com.billing.simple.billsoft.service.CustomerBalanceService;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.StatementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BusinessPartyLinkServiceImpl implements BusinessPartyLinkService {

    private final BusinessPartyLinkRepository linkRepo;
    private final CustomerRepository customerRepo;
    private final PartyRepository partyRepo;
    private final CustomerBalanceService customerBalanceService;
    private final PartyService partyService;
    private final StatementService statementService;

    public BusinessPartyLinkServiceImpl(
            BusinessPartyLinkRepository linkRepo,
            CustomerRepository customerRepo,
            PartyRepository partyRepo,
            CustomerBalanceService customerBalanceService,
            PartyService partyService,
            StatementService statementService) {
        this.linkRepo = linkRepo;
        this.customerRepo = customerRepo;
        this.partyRepo = partyRepo;
        this.customerBalanceService = customerBalanceService;
        this.partyService = partyService;
        this.statementService = statementService;
    }

    private Long resolveFirmId() {
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId == null || firmId <= 0) {
            throw new TenantSecurityException("Active firm context is required for counterparty operations");
        }
        return firmId;
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse link(BusinessPartyLinkRequest request) {
        if (request == null || request.getCustomerId() == null || request.getPartyId() == null) {
            throw new IllegalArgumentException("Customer ID and Party ID are both required to establish a link");
        }

        Long firmId = resolveFirmId();
        Long customerId = request.getCustomerId();
        Long partyId = request.getPartyId();

        // 1. Verify Customer exists and belongs to current firm
        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in current firm"));

        // 2. Verify Party exists and belongs to current firm
        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party #" + partyId + " not found in current firm"));

        // 3. Same-firm consistency verification
        if (!firmId.equals(customer.getFirmId()) || !firmId.equals(party.getFirmId())) {
            throw new TenantSecurityException("Cross-firm link rejected: Customer firm " + customer.getFirmId() + " or Party firm " + party.getFirmId() + " does not match active firm " + firmId);
        }

        // 4. Verify 1:1 constraints: Customer not already linked
        if (linkRepo.existsByFirmIdAndCustomerId(firmId, customerId)) {
            throw new IllegalStateException("Customer #" + customerId + " is already linked to a Supplier in this firm");
        }

        // 5. Verify 1:1 constraints: Party not already linked
        if (linkRepo.existsByFirmIdAndPartyId(firmId, partyId)) {
            throw new IllegalStateException("Supplier #" + partyId + " is already linked to a Customer in this firm");
        }

        // 6. Persist explicit link
        BusinessPartyLink entity = BusinessPartyLink.builder()
                .firmId(firmId)
                .customerId(customerId)
                .partyId(partyId)
                .linkedAt(LocalDateTime.now())
                .linkedBy(request.getLinkedBy() != null && !request.getLinkedBy().isBlank() ? request.getLinkedBy().trim() : "System")
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .build();

        BusinessPartyLink saved = linkRepo.save(entity);
        return buildResponse(saved, customer, party, firmId);
    }

    @Override
    @Transactional
    public void unlink(Long customerId, Long partyId) {
        if (customerId == null || partyId == null) {
            throw new IllegalArgumentException("Both Customer ID and Party ID are required to unlink");
        }
        Long firmId = resolveFirmId();

        BusinessPartyLink link = linkRepo.findByFirmIdAndCustomerIdAndPartyId(firmId, customerId, partyId)
                .orElseThrow(() -> new IllegalArgumentException("No active link found between Customer #" + customerId + " and Supplier #" + partyId + " in current firm"));

        linkRepo.delete(link);
    }

    @Override
    @Transactional
    public void unlink(Long linkId) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required to unlink");
        }
        Long firmId = resolveFirmId();
        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));
        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        linkRepo.delete(link);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessPartyLinkResponse getLinkedPartyForCustomer(Long customerId) {
        if (customerId == null) return null;
        Long firmId = resolveFirmId();

        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in current firm"));

        return linkRepo.findByFirmIdAndCustomerId(firmId, customerId)
                .map(link -> {
                    Party party = partyRepo.findByIdAndFirmId(link.getPartyId(), firmId).orElse(null);
                    return buildResponse(link, customer, party, firmId);
                })
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessPartyLinkResponse getLinkedCustomerForParty(Long partyId) {
        if (partyId == null) return null;
        Long firmId = resolveFirmId();

        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party #" + partyId + " not found in current firm"));

        return linkRepo.findByFirmIdAndPartyId(firmId, partyId)
                .map(link -> {
                    Customer customer = customerRepo.findByIdAndFirmId(link.getCustomerId(), firmId).orElse(null);
                    return buildResponse(link, customer, party, firmId);
                })
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyLinkCandidateDto> searchCandidates(String type, String query) {
        Long firmId = resolveFirmId();
        String q = (query != null) ? query.trim().toLowerCase() : "";

        List<BusinessPartyLink> existingLinks = linkRepo.findByFirmId(firmId);

        if ("CUSTOMER".equalsIgnoreCase(type)) {
            // Find unlinked customers
            Set<Long> linkedCustomerIds = existingLinks.stream()
                    .map(BusinessPartyLink::getCustomerId)
                    .collect(Collectors.toSet());

            List<Customer> allCustomers = customerRepo.findByFirmIdOrderByNameAsc(firmId);
            return allCustomers.stream()
                    .filter(c -> !linkedCustomerIds.contains(c.getId()))
                    .filter(c -> q.isEmpty() ||
                            (c.getName() != null && c.getName().toLowerCase().contains(q)) ||
                            (c.getPhone() != null && c.getPhone().toLowerCase().contains(q)) ||
                            (c.getGstin() != null && c.getGstin().toLowerCase().contains(q)))
                    .limit(25)
                    .map(c -> {
                        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmId, c.getId());
                        double balanceVal = bal != null && bal.getClosingBalance() != null ? bal.getClosingBalance().doubleValue() : 0.0;
                        return PartyLinkCandidateDto.builder()
                                .id(c.getId())
                                .type("CUSTOMER")
                                .name(c.getName())
                                .phone(c.getPhone())
                                .gstin(c.getGstin())
                                .address(c.getAddress())
                                .balance(balanceVal)
                                .balanceStatus(balanceVal > 0 ? "RECEIVABLE" : (balanceVal < 0 ? "ADVANCE" : "SETTLED"))
                                .build();
                    })
                    .collect(Collectors.toList());
        } else {
            // Default to searching PARTY candidates
            Set<Long> linkedPartyIds = existingLinks.stream()
                    .map(BusinessPartyLink::getPartyId)
                    .collect(Collectors.toSet());

            List<Party> allParties = partyRepo.findByFirmIdOrderByNameAsc(firmId);
            return allParties.stream()
                    .filter(p -> !linkedPartyIds.contains(p.getId()))
                    .filter(p -> q.isEmpty() ||
                            (p.getName() != null && p.getName().toLowerCase().contains(q)) ||
                            (p.getPhone() != null && p.getPhone().toLowerCase().contains(q)) ||
                            (p.getGstin() != null && p.getGstin().toLowerCase().contains(q)) ||
                            (p.getContactPerson() != null && p.getContactPerson().toLowerCase().contains(q)))
                    .limit(25)
                    .map(p -> {
                        PartyFinancialSummary fin = partyService.getFinancialSummary(p.getId(), firmId);
                        double balanceVal = fin != null && fin.getNetBalance() != null ? fin.getNetBalance().doubleValue() : 0.0;
                        String status = fin != null && fin.getBalanceStatus() != null ? fin.getBalanceStatus() : "SETTLED";
                        return PartyLinkCandidateDto.builder()
                                .id(p.getId())
                                .type("PARTY")
                                .name(p.getName())
                                .phone(p.getPhone())
                                .gstin(p.getGstin())
                                .address(p.getAddress())
                                .city(p.getCity())
                                .state(p.getState())
                                .contactPerson(p.getContactPerson())
                                .balance(balanceVal)
                                .balanceStatus(status)
                                .build();
                    })
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFirmLinksMap(Long firmIdParam) {
        Long firmId = firmIdParam != null ? firmIdParam : resolveFirmId();
        List<BusinessPartyLink> links = linkRepo.findByFirmId(firmId);

        Map<Long, Map<String, Object>> customerToParty = new HashMap<>();
        Map<Long, Map<String, Object>> partyToCustomer = new HashMap<>();
        Set<Long> linkedCustomerIds = new HashSet<>();
        Set<Long> linkedPartyIds = new HashSet<>();

        if (!links.isEmpty()) {
            Set<Long> custIds = links.stream().map(BusinessPartyLink::getCustomerId).collect(Collectors.toSet());
            Set<Long> ptyIds = links.stream().map(BusinessPartyLink::getPartyId).collect(Collectors.toSet());

            Map<Long, Customer> custMap = customerRepo.findAllById(custIds).stream()
                    .collect(Collectors.toMap(Customer::getId, c -> c));
            Map<Long, Party> ptyMap = partyRepo.findAllById(ptyIds).stream()
                    .collect(Collectors.toMap(Party::getId, p -> p));

            for (BusinessPartyLink link : links) {
                Long cId = link.getCustomerId();
                Long pId = link.getPartyId();
                linkedCustomerIds.add(cId);
                linkedPartyIds.add(pId);

                Customer c = custMap.get(cId);
                Party p = ptyMap.get(pId);

                Map<String, Object> pInfo = new HashMap<>();
                pInfo.put("partyId", pId);
                pInfo.put("partyName", p != null ? p.getName() : "Unknown Supplier");
                pInfo.put("partyPhone", p != null ? p.getPhone() : null);
                pInfo.put("linkId", link.getId());
                customerToParty.put(cId, pInfo);

                Map<String, Object> cInfo = new HashMap<>();
                cInfo.put("customerId", cId);
                cInfo.put("customerName", c != null ? c.getName() : "Unknown Customer");
                cInfo.put("customerPhone", c != null ? c.getPhone() : null);
                cInfo.put("linkId", link.getId());
                partyToCustomer.put(pId, cInfo);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("customerToParty", customerToParty);
        result.put("partyToCustomer", partyToCustomer);
        result.put("linkedCustomerIds", linkedCustomerIds);
        result.put("linkedPartyIds", linkedPartyIds);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public UnifiedCounterpartyStatementResponse getUnifiedStatement(Long customerId, Long partyId, LocalDate from, LocalDate to) {
        Long firmId = resolveFirmId();

        // Validate explicit link existence
        BusinessPartyLink link = linkRepo.findByFirmIdAndCustomerIdAndPartyId(firmId, customerId, partyId)
                .orElseThrow(() -> new IllegalArgumentException("No active link found between Customer #" + customerId + " and Supplier #" + partyId));

        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in firm"));

        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier #" + partyId + " not found in firm"));

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        // Fetch independent customer and party statements using existing authoritative engines
        CustomerStatementResponse custStatement = statementService.getCustomerStatement(firmId, customerId, fromDate, toDate);
        PartyStatementResponse ptyStatement = statementService.getPartyStatement(firmId, partyId, fromDate, toDate);

        double totalReceivables = custStatement.getClosingBalance() != null ? custStatement.getClosingBalance() : 0.0;
        double totalPayables = ptyStatement.getClosingBalance() != null ? ptyStatement.getClosingBalance() : 0.0;

        // Net position: Positive (+) = they owe us; Negative (-) = we owe them
        double rawNet = totalReceivables - totalPayables;
        double netPosition = BigDecimal.valueOf(rawNet).setScale(2, RoundingMode.HALF_UP).doubleValue();

        String label;
        if (netPosition > 0.005) {
            label = "NET_RECEIVABLE";
        } else if (netPosition < -0.005) {
            label = "NET_PAYABLE";
        } else {
            label = "BALANCED";
        }

        // Build combined chronological entries
        List<UnifiedStatementEntry> entries = new ArrayList<>();

        if (custStatement.getEntries() != null) {
            for (StatementEntry e : custStatement.getEntries()) {
                entries.add(UnifiedStatementEntry.builder()
                        .date(e.getDate())
                        .side("CUSTOMER_RECEIVABLE")
                        .type(e.getType())
                        .ref(e.getRef())
                        .description(e.getDescription())
                        .receivableDebit(e.getDebit() != null ? e.getDebit() : 0.0)
                        .receivableCredit(e.getCredit() != null ? e.getCredit() : 0.0)
                        .payableDebit(0.0)
                        .payableCredit(0.0)
                        .build());
            }
        }

        if (ptyStatement.getEntries() != null) {
            for (StatementEntry e : ptyStatement.getEntries()) {
                entries.add(UnifiedStatementEntry.builder()
                        .date(e.getDate())
                        .side("SUPPLIER_PAYABLE")
                        .type(e.getType())
                        .ref(e.getRef())
                        .description(e.getDescription())
                        .receivableDebit(0.0)
                        .receivableCredit(0.0)
                        .payableDebit(e.getDebit() != null ? e.getDebit() : 0.0)
                        .payableCredit(e.getCredit() != null ? e.getCredit() : 0.0)
                        .build());
            }
        }

        entries.sort(Comparator.comparing(UnifiedStatementEntry::getDate, Comparator.nullsLast(Comparator.naturalOrder())));

        return UnifiedCounterpartyStatementResponse.builder()
                .customerId(customerId)
                .customerName(customer.getName())
                .partyId(partyId)
                .partyName(party.getName())
                .from(fromDate)
                .to(toDate)
                .totalReceivables(totalReceivables)
                .totalPayables(totalPayables)
                .netPosition(netPosition)
                .netPositionLabel(label)
                .disclaimer("READ-ONLY COMBINED COUNTERPARTY VIEW (NOT A LEGAL SETTLEMENT OR ACCOUNTING VOUCHER)")
                .entries(entries)
                .customerStatement(custStatement)
                .supplierStatement(ptyStatement)
                .build();
    }

    private BusinessPartyLinkResponse buildResponse(BusinessPartyLink link, Customer customer, Party party, Long firmId) {
        Double receivable = null;
        if (customer != null) {
            try {
                CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmId, customer.getId());
                if (bal != null && bal.getClosingBalance() != null) {
                    receivable = bal.getClosingBalance().doubleValue();
                }
            } catch (Exception ignored) {}
        }

        Double payable = null;
        String partyStatus = null;
        if (party != null) {
            try {
                PartyFinancialSummary fin = partyService.getFinancialSummary(party.getId(), firmId);
                if (fin != null && fin.getNetBalance() != null) {
                    payable = fin.getNetBalance().doubleValue();
                    partyStatus = fin.getBalanceStatus();
                }
            } catch (Exception ignored) {}
        }

        return BusinessPartyLinkResponse.builder()
                .id(link.getId())
                .firmId(link.getFirmId())
                .customerId(link.getCustomerId())
                .partyId(link.getPartyId())
                .linkedAt(link.getLinkedAt())
                .linkedBy(link.getLinkedBy())
                .notes(link.getNotes())
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .customerGstin(customer != null ? customer.getGstin() : null)
                .customerAddress(customer != null ? customer.getAddress() : null)
                .customerNetReceivable(receivable)
                .partyName(party != null ? party.getName() : null)
                .partyPhone(party != null ? party.getPhone() : null)
                .partyGstin(party != null ? party.getGstin() : null)
                .partyAddress(party != null ? party.getAddress() : null)
                .partyContactPerson(party != null ? party.getContactPerson() : null)
                .partyNetPayable(payable)
                .partyBalanceStatus(partyStatus)
                .build();
    }
}
