package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.BusinessIdentityAlias;
import com.billing.simple.billsoft.entities.BusinessPartyLink;
import com.billing.simple.billsoft.entities.BusinessPartyLinkEvent;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repositories.BusinessIdentityAliasRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkEventRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.AdminAuthorizationException;
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
    private final BusinessIdentityAliasRepository aliasRepo;
    private final BusinessPartyLinkEventRepository eventRepo;
    private final AppConfigRepository appConfigRepo;

    public BusinessPartyLinkServiceImpl(
            BusinessPartyLinkRepository linkRepo,
            CustomerRepository customerRepo,
            PartyRepository partyRepo,
            CustomerBalanceService customerBalanceService,
            PartyService partyService,
            StatementService statementService,
            BusinessIdentityAliasRepository aliasRepo,
            BusinessPartyLinkEventRepository eventRepo,
            AppConfigRepository appConfigRepo) {
        this.linkRepo = linkRepo;
        this.customerRepo = customerRepo;
        this.partyRepo = partyRepo;
        this.customerBalanceService = customerBalanceService;
        this.partyService = partyService;
        this.statementService = statementService;
        this.aliasRepo = aliasRepo;
        this.eventRepo = eventRepo;
        this.appConfigRepo = appConfigRepo;
    }

    private Long resolveFirmId() {
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId == null || firmId <= 0) {
            throw new TenantSecurityException("Active firm context is required for counterparty operations");
        }
        return firmId;
    }

    private static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    private boolean isActivelyLinked(BusinessPartyLink link) {
        if (link == null) return false;
        String s = link.getStatus();
        return !"UNLINKED".equalsIgnoreCase(s);
    }

    private boolean isCustomerLinked(Long firmId, Long customerId) {
        if (linkRepo.existsByFirmIdAndCustomerId(firmId, customerId)) {
            Optional<BusinessPartyLink> opt = linkRepo.findByFirmIdAndCustomerId(firmId, customerId);
            if (opt.isPresent()) {
                return isActivelyLinked(opt.get());
            }
            return true;
        }
        if (linkRepo.existsByFirmIdAndActiveCustomerId(firmId, customerId)) {
            return true;
        }
        return false;
    }

    private boolean isPartyLinked(Long firmId, Long partyId) {
        if (linkRepo.existsByFirmIdAndPartyId(firmId, partyId)) {
            Optional<BusinessPartyLink> opt = linkRepo.findByFirmIdAndPartyId(firmId, partyId);
            if (opt.isPresent()) {
                return isActivelyLinked(opt.get());
            }
            return true;
        }
        if (linkRepo.existsByFirmIdAndActivePartyId(firmId, partyId)) {
            return true;
        }
        return false;
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

        // 4. Verify 1:1 constraints: Customer not already actively linked
        if (isCustomerLinked(firmId, customerId)) {
            throw new IllegalStateException("Customer #" + customerId + " is already linked to a Supplier in this firm");
        }

        // 5. Verify 1:1 constraints: Party not already actively linked
        if (isPartyLinked(firmId, partyId)) {
            throw new IllegalStateException("Supplier #" + partyId + " is already linked to a Customer in this firm");
        }

        int maxVer = linkRepo.findMaxRelationshipVersion(firmId, customerId, partyId);
        int nextVer = (maxVer > 0) ? (maxVer + 1) : 1;

        // Establish canonical name: if names differ, retain party's name as an alias and set both to canonical customer name (or request override)
        String canonicalName = hasText(customer.getName()) ? customer.getName().trim() : party.getName().trim();
        if (hasText(party.getName()) && !party.getName().trim().equalsIgnoreCase(canonicalName)) {
            recordAliasInternal(firmId, null, customerId, partyId, party.getName().trim(), "PREVIOUS_NAME", "PARTY", "Preserved previous supplier name prior to linking");
            party.setName(canonicalName);
            partyRepo.save(party);
        }

        // 6. Persist explicit link
        BusinessPartyLink entity = BusinessPartyLink.builder()
                .firmId(firmId)
                .customerId(customerId)
                .partyId(partyId)
                .activeCustomerId(customerId)
                .activePartyId(partyId)
                .linkedAt(LocalDateTime.now())
                .linkedBy(hasText(request.getLinkedBy()) ? request.getLinkedBy().trim() : "System")
                .notes(hasText(request.getNotes()) ? request.getNotes().trim() : null)
                .status("ACTIVE")
                .customerRoleActive(Boolean.TRUE)
                .partyRoleActive(Boolean.TRUE)
                .relationshipVersion(nextVer)
                .statusUpdatedAt(LocalDateTime.now())
                .build();

        BusinessPartyLink saved = linkRepo.save(entity);

        recordAuditEvent(firmId, saved.getId(), nextVer, customerId, partyId, "LINK_CREATED", entity.getLinkedBy(), null, canonicalName, "Explicit 1:1 link created");

        return buildResponse(saved, customer, party, firmId);
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse enableCustomerRole(EnableCustomerRoleRequest request) {
        if (request == null || request.getPartyId() == null) {
            throw new IllegalArgumentException("Party ID is required to enable Customer role");
        }

        Long firmId = resolveFirmId();
        Long partyId = request.getPartyId();

        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party #" + partyId + " not found in current firm"));

        Optional<BusinessPartyLink> existingOpt = linkRepo.findByFirmIdAndPartyId(firmId, partyId);
        if (existingOpt != null && existingOpt.isPresent() && isActivelyLinked(existingOpt.get())) {
            BusinessPartyLink existing = existingOpt.get();
            if (!Boolean.FALSE.equals(existing.getCustomerRoleActive())) {
                throw new IllegalStateException("Supplier #" + partyId + " is already linked to a Customer in this firm");
            }
            existing.setCustomerRoleActive(Boolean.TRUE);
            existing.setStatus(computeLinkStatus(true, !Boolean.FALSE.equals(existing.getPartyRoleActive())));
            existing.setDisabledReason(null);
            existing.setStatusUpdatedAt(LocalDateTime.now());
            BusinessPartyLink saved = linkRepo.save(existing);
            Customer existingCustomer = customerRepo.findByIdAndFirmId(existing.getCustomerId(), firmId).orElse(null);
            recordAuditEvent(firmId, saved.getId(), saved.getRelationshipVersion(), existing.getCustomerId(), partyId, "ROLE_ACTIVATED", request.getLinkedBy(), "CUSTOMER_DISABLED", "ACTIVE", "Customer role re-activated");
            return buildResponse(saved, existingCustomer, party, firmId);
        } else if (linkRepo.existsByFirmIdAndActivePartyId(firmId, partyId) || linkRepo.existsByFirmIdAndPartyId(firmId, partyId)) {
            throw new IllegalStateException("Supplier #" + partyId + " is already linked to a Customer in this firm");
        }

        String custName = hasText(request.getCustomerName()) ? request.getCustomerName().trim() : party.getName();
        String custPhone = hasText(request.getPhone()) ? request.getPhone().trim() : party.getPhone();
        String custEmail = hasText(request.getEmail()) ? request.getEmail().trim() : party.getEmail();
        String custAddress = hasText(request.getAddress()) ? request.getAddress().trim() : party.getAddress();
        String custGstin = hasText(request.getGstin()) ? request.getGstin().trim() : party.getGstin();

        Customer targetCustomer = null;

        if (request.getTargetCustomerId() != null) {
            // Explicitly selected candidate by user
            targetCustomer = customerRepo.findByIdAndFirmId(request.getTargetCustomerId(), firmId)
                    .orElseThrow(() -> new IllegalArgumentException("Target Customer #" + request.getTargetCustomerId() + " not found in current firm"));
            if (linkRepo.existsByFirmIdAndActiveCustomerId(firmId, targetCustomer.getId())) {
                throw new IllegalStateException("Selected Customer #" + targetCustomer.getId() + " is already linked to another Supplier in this firm");
            }
        } else if (!Boolean.TRUE.equals(request.getConfirmCreateNew())) {
            // Discover candidate matches
            List<CandidateMatch<Customer>> candidates = findCandidateCustomers(firmId, custGstin, custPhone, custName);
            if (candidates.size() > 1) {
                throw new IllegalStateException("AMBIGUOUS_MATCH: Multiple candidate Customers found (" + candidates.size() + "). Explicit counterpart selection is required.");
            } else if (candidates.size() == 1) {
                CandidateMatch<Customer> cMatch = candidates.get(0);
                if (cMatch.isGstinMismatch()) {
                    throw new IllegalStateException("Candidate Customer #" + cMatch.entity.getId() + " has different GSTIN (" + cMatch.entity.getGstin() + " vs " + custGstin + "). Explicit review and selection required.");
                }
                // Exactly 1 unambiguous match
                targetCustomer = cMatch.entity;
            }
        }

        // Otherwise create new Customer
        if (targetCustomer == null) {
            Customer newCustomer = Customer.builder()
                    .firmId(firmId)
                    .name(custName)
                    .phone(custPhone)
                    .email(custEmail)
                    .address(custAddress)
                    .gstin(custGstin)
                    .build();
            targetCustomer = customerRepo.save(newCustomer);
        }

        // Align canonical name
        if (!custName.equalsIgnoreCase(targetCustomer.getName())) {
            recordAliasInternal(firmId, null, targetCustomer.getId(), partyId, targetCustomer.getName(), "PREVIOUS_NAME", "CUSTOMER", "Preserved previous customer name before role activation");
            targetCustomer.setName(custName);
            Customer savedCust = customerRepo.save(targetCustomer);
            if (savedCust != null) {
                targetCustomer = savedCust;
            }
        }

        int maxVer = linkRepo.findMaxRelationshipVersion(firmId, targetCustomer.getId(), partyId);
        int nextVer = (maxVer > 0) ? (maxVer + 1) : 1;

        // Persist BusinessPartyLink
        BusinessPartyLink entity = BusinessPartyLink.builder()
                .firmId(firmId)
                .customerId(targetCustomer.getId())
                .partyId(partyId)
                .activeCustomerId(targetCustomer.getId())
                .activePartyId(partyId)
                .linkedAt(LocalDateTime.now())
                .linkedBy(hasText(request.getLinkedBy()) ? request.getLinkedBy().trim() : "System")
                .notes(hasText(request.getNotes()) ? request.getNotes().trim() : "Enabled customer role from supplier")
                .status("ACTIVE")
                .customerRoleActive(Boolean.TRUE)
                .partyRoleActive(Boolean.TRUE)
                .relationshipVersion(nextVer)
                .statusUpdatedAt(LocalDateTime.now())
                .build();

        BusinessPartyLink saved = linkRepo.save(entity);

        recordAuditEvent(firmId, saved.getId(), nextVer, targetCustomer.getId(), partyId, "ROLE_ACTIVATED", entity.getLinkedBy(), null, "ACTIVE", "Customer role enabled for supplier #" + partyId);

        return buildResponse(saved, targetCustomer, party, firmId);
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse enablePartyRole(EnablePartyRoleRequest request) {
        if (request == null || request.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID is required to enable Supplier role");
        }

        Long firmId = resolveFirmId();
        Long customerId = request.getCustomerId();

        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in current firm"));

        Optional<BusinessPartyLink> existingOpt = linkRepo.findByFirmIdAndCustomerId(firmId, customerId);
        if (existingOpt != null && existingOpt.isPresent() && isActivelyLinked(existingOpt.get())) {
            BusinessPartyLink existing = existingOpt.get();
            if (!Boolean.FALSE.equals(existing.getPartyRoleActive())) {
                throw new IllegalStateException("Customer #" + customerId + " is already linked to a Supplier in this firm");
            }
            existing.setPartyRoleActive(Boolean.TRUE);
            existing.setStatus(computeLinkStatus(!Boolean.FALSE.equals(existing.getCustomerRoleActive()), true));
            existing.setDisabledReason(null);
            existing.setStatusUpdatedAt(LocalDateTime.now());
            BusinessPartyLink saved = linkRepo.save(existing);
            Party existingParty = partyRepo.findByIdAndFirmId(existing.getPartyId(), firmId).orElse(null);
            recordAuditEvent(firmId, saved.getId(), saved.getRelationshipVersion(), customerId, existing.getPartyId(), "ROLE_ACTIVATED", request.getLinkedBy(), "PARTY_DISABLED", "ACTIVE", "Supplier role re-activated");
            return buildResponse(saved, customer, existingParty, firmId);
        } else if (linkRepo.existsByFirmIdAndActiveCustomerId(firmId, customerId) || linkRepo.existsByFirmIdAndCustomerId(firmId, customerId)) {
            throw new IllegalStateException("Customer #" + customerId + " is already linked to a Supplier in this firm");
        }

        String ptyName = hasText(request.getPartyName()) ? request.getPartyName().trim() : customer.getName();
        String ptyPhone = hasText(request.getPhone()) ? request.getPhone().trim() : customer.getPhone();
        String ptyEmail = hasText(request.getEmail()) ? request.getEmail().trim() : customer.getEmail();
        String ptyAddress = hasText(request.getAddress()) ? request.getAddress().trim() : customer.getAddress();
        String ptyGstin = hasText(request.getGstin()) ? request.getGstin().trim() : customer.getGstin();

        Party targetParty = null;

        if (request.getTargetPartyId() != null) {
            // Explicitly selected candidate by user
            targetParty = partyRepo.findByIdAndFirmId(request.getTargetPartyId(), firmId)
                    .orElseThrow(() -> new IllegalArgumentException("Target Supplier #" + request.getTargetPartyId() + " not found in current firm"));
            if (linkRepo.existsByFirmIdAndActivePartyId(firmId, targetParty.getId())) {
                throw new IllegalStateException("Selected Supplier #" + targetParty.getId() + " is already linked to another Customer in this firm");
            }
        } else if (!Boolean.TRUE.equals(request.getConfirmCreateNew())) {
            // Discover candidate matches
            List<CandidateMatch<Party>> candidates = findCandidateParties(firmId, ptyGstin, ptyPhone, ptyName);
            if (candidates.size() > 1) {
                throw new IllegalStateException("AMBIGUOUS_MATCH: Multiple candidate Suppliers found (" + candidates.size() + "). Explicit counterpart selection is required.");
            } else if (candidates.size() == 1) {
                CandidateMatch<Party> pMatch = candidates.get(0);
                if (pMatch.isGstinMismatch()) {
                    throw new IllegalStateException("Candidate Supplier #" + pMatch.entity.getId() + " has different GSTIN (" + pMatch.entity.getGstin() + " vs " + ptyGstin + "). Explicit review and selection required.");
                }
                // Exactly 1 unambiguous match
                targetParty = pMatch.entity;
            }
        }

        // Otherwise create new Party
        if (targetParty == null) {
            Party newParty = Party.builder()
                    .firmId(firmId)
                    .name(ptyName)
                    .contactPerson(request.getContactPerson())
                    .phone(ptyPhone)
                    .email(ptyEmail)
                    .address(ptyAddress)
                    .city(request.getCity())
                    .state(request.getState())
                    .pincode(request.getPincode())
                    .gstin(ptyGstin)
                    .pan(request.getPan())
                    .bankName(request.getBankName())
                    .bankAccount(request.getBankAccount())
                    .bankIfsc(request.getBankIfsc())
                    .upiId(request.getUpiId())
                    .openingBalance(request.getOpeningBalance() != null ? request.getOpeningBalance() : BigDecimal.ZERO)
                    .openingBalanceType(hasText(request.getOpeningBalanceType()) ? request.getOpeningBalanceType().trim().toUpperCase() : "PAYABLE")
                    .notes(request.getNotes())
                    .build();
            targetParty = partyRepo.save(newParty);
        }

        // Align canonical name
        if (!ptyName.equalsIgnoreCase(targetParty.getName())) {
            recordAliasInternal(firmId, null, customerId, targetParty.getId(), targetParty.getName(), "PREVIOUS_NAME", "PARTY", "Preserved previous supplier name before role activation");
            targetParty.setName(ptyName);
            Party savedPty = partyRepo.save(targetParty);
            if (savedPty != null) {
                targetParty = savedPty;
            }
        }

        int maxVer = linkRepo.findMaxRelationshipVersion(firmId, customerId, targetParty.getId());
        int nextVer = (maxVer > 0) ? (maxVer + 1) : 1;

        // Persist BusinessPartyLink
        BusinessPartyLink entity = BusinessPartyLink.builder()
                .firmId(firmId)
                .customerId(customerId)
                .partyId(targetParty.getId())
                .activeCustomerId(customerId)
                .activePartyId(targetParty.getId())
                .linkedAt(LocalDateTime.now())
                .linkedBy(hasText(request.getLinkedBy()) ? request.getLinkedBy().trim() : "System")
                .notes(hasText(request.getNotes()) ? request.getNotes().trim() : "Enabled supplier role from customer")
                .status("ACTIVE")
                .customerRoleActive(Boolean.TRUE)
                .partyRoleActive(Boolean.TRUE)
                .relationshipVersion(nextVer)
                .statusUpdatedAt(LocalDateTime.now())
                .build();

        BusinessPartyLink saved = linkRepo.save(entity);

        recordAuditEvent(firmId, saved.getId(), nextVer, customerId, targetParty.getId(), "ROLE_ACTIVATED", entity.getLinkedBy(), null, "ACTIVE", "Supplier role enabled for customer #" + customerId);

        return buildResponse(saved, customer, targetParty, firmId);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleMatchPreviewResponse previewEnableCustomerRole(Long partyId, String gstin, String phone) {
        if (partyId == null) {
            throw new IllegalArgumentException("Party ID is required to preview Customer role");
        }
        Long firmId = resolveFirmId();
        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party #" + partyId + " not found in current firm"));

        Optional<BusinessPartyLink> existingOpt = linkRepo.findByFirmIdAndPartyId(firmId, partyId);
        if (existingOpt.isPresent() && isActivelyLinked(existingOpt.get())) {
            BusinessPartyLink existing = existingOpt.get();
            Customer existingCust = customerRepo.findByIdAndFirmId(existing.getCustomerId(), firmId).orElse(null);
            boolean active = !Boolean.FALSE.equals(existing.getCustomerRoleActive());
            return RoleMatchPreviewResponse.builder()
                    .targetRole("CUSTOMER")
                    .resolution(active ? "ALREADY_LINKED" : "REUSE_EXISTING")
                    .matchedBy("LINKED_RECORD")
                    .existingEntityId(existing.getCustomerId())
                    .existingEntityName(existingCust != null ? existingCust.getName() : null)
                    .existingEntityPhone(existingCust != null ? existingCust.getPhone() : null)
                    .existingEntityGstin(existingCust != null ? existingCust.getGstin() : null)
                    .existingEntityEmail(existingCust != null ? existingCust.getEmail() : null)
                    .existingEntityAddress(existingCust != null ? existingCust.getAddress() : null)
                    .message(active
                            ? "This supplier is already linked to an active Customer profile."
                            : "Previously linked Customer role is inactive and will be re-enabled, reusing Customer #" + existing.getCustomerId() + ".")
                    .build();
        }

        String effGstin = hasText(gstin) ? gstin.trim() : party.getGstin();
        String effPhone = hasText(phone) ? phone.trim() : party.getPhone();
        List<CandidateMatch<Customer>> candidates = findCandidateCustomers(firmId, effGstin, effPhone, party.getName());

        if (candidates.size() > 1) {
            List<PartyLinkCandidateDto> candidateDtos = candidates.stream().map(c -> {
                Customer cust = c.entity;
                CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmId, cust.getId());
                double balanceVal = bal != null && bal.getClosingBalance() != null ? bal.getClosingBalance().doubleValue() : 0.0;
                return PartyLinkCandidateDto.builder()
                        .id(cust.getId())
                        .type("CUSTOMER")
                        .name(cust.getName())
                        .phone(cust.getPhone())
                        .gstin(cust.getGstin())
                        .address(cust.getAddress())
                        .balance(balanceVal)
                        .balanceStatus(balanceVal > 0 ? "RECEIVABLE" : (balanceVal < 0 ? "ADVANCE" : "SETTLED"))
                        .build();
            }).collect(Collectors.toList());

            return RoleMatchPreviewResponse.builder()
                    .targetRole("CUSTOMER")
                    .resolution("AMBIGUOUS_MATCH")
                    .matchedBy("MULTIPLE")
                    .candidates(candidateDtos)
                    .message("Multiple candidate Customers (" + candidates.size() + ") match by GSTIN, Phone, or Name. Please select one explicitly or confirm creating a new Customer.")
                    .build();
        } else if (candidates.size() == 1) {
            CandidateMatch<Customer> cMatch = candidates.get(0);
            Customer matched = cMatch.entity;
            return RoleMatchPreviewResponse.builder()
                    .targetRole("CUSTOMER")
                    .resolution("REUSE_EXISTING")
                    .matchedBy(cMatch.matchType)
                    .existingEntityId(matched.getId())
                    .existingEntityName(matched.getName())
                    .existingEntityPhone(matched.getPhone())
                    .existingEntityGstin(matched.getGstin())
                    .existingEntityEmail(matched.getEmail())
                    .existingEntityAddress(matched.getAddress())
                    .gstinMismatch(cMatch.isGstinMismatch())
                    .message("Existing unlinked Customer #" + matched.getId() + " (" + matched.getName() + ") matched by " + cMatch.matchType + (cMatch.isGstinMismatch() ? " (Note: GSTIN differs)" : "") + ". Require explicit confirmation before linking.")
                    .build();
        }

        return RoleMatchPreviewResponse.builder()
                .targetRole("CUSTOMER")
                .resolution("CREATE_NEW")
                .matchedBy("NONE")
                .message("No matching unlinked Customer found. A new Customer profile will be created and linked.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleMatchPreviewResponse previewEnablePartyRole(Long customerId, String gstin, String phone) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required to preview Supplier role");
        }
        Long firmId = resolveFirmId();
        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in current firm"));

        Optional<BusinessPartyLink> existingOpt = linkRepo.findByFirmIdAndCustomerId(firmId, customerId);
        if (existingOpt.isPresent() && isActivelyLinked(existingOpt.get())) {
            BusinessPartyLink existing = existingOpt.get();
            Party existingParty = partyRepo.findByIdAndFirmId(existing.getPartyId(), firmId).orElse(null);
            boolean active = !Boolean.FALSE.equals(existing.getPartyRoleActive());
            return RoleMatchPreviewResponse.builder()
                    .targetRole("PARTY")
                    .resolution(active ? "ALREADY_LINKED" : "REUSE_EXISTING")
                    .matchedBy("LINKED_RECORD")
                    .existingEntityId(existing.getPartyId())
                    .existingEntityName(existingParty != null ? existingParty.getName() : null)
                    .existingEntityPhone(existingParty != null ? existingParty.getPhone() : null)
                    .existingEntityGstin(existingParty != null ? existingParty.getGstin() : null)
                    .existingEntityEmail(existingParty != null ? existingParty.getEmail() : null)
                    .existingEntityAddress(existingParty != null ? existingParty.getAddress() : null)
                    .message(active
                            ? "This customer is already linked to an active Supplier profile."
                            : "Previously linked Supplier role is inactive and will be re-enabled, reusing Supplier #" + existing.getPartyId() + ".")
                    .build();
        }

        String effGstin = hasText(gstin) ? gstin.trim() : customer.getGstin();
        String effPhone = hasText(phone) ? phone.trim() : customer.getPhone();
        List<CandidateMatch<Party>> candidates = findCandidateParties(firmId, effGstin, effPhone, customer.getName());

        if (candidates.size() > 1) {
            List<PartyLinkCandidateDto> candidateDtos = candidates.stream().map(p -> {
                Party pty = p.entity;
                PartyFinancialSummary fin = partyService.getFinancialSummary(pty.getId(), firmId);
                double balanceVal = fin != null && fin.getNetBalance() != null ? fin.getNetBalance().doubleValue() : 0.0;
                String status = fin != null && fin.getBalanceStatus() != null ? fin.getBalanceStatus() : "SETTLED";
                return PartyLinkCandidateDto.builder()
                        .id(pty.getId())
                        .type("PARTY")
                        .name(pty.getName())
                        .phone(pty.getPhone())
                        .gstin(pty.getGstin())
                        .address(pty.getAddress())
                        .city(pty.getCity())
                        .state(pty.getState())
                        .contactPerson(pty.getContactPerson())
                        .balance(balanceVal)
                        .balanceStatus(status)
                        .build();
            }).collect(Collectors.toList());

            return RoleMatchPreviewResponse.builder()
                    .targetRole("PARTY")
                    .resolution("AMBIGUOUS_MATCH")
                    .matchedBy("MULTIPLE")
                    .candidates(candidateDtos)
                    .message("Multiple candidate Suppliers (" + candidates.size() + ") match by GSTIN, Phone, or Name. Please select one explicitly or confirm creating a new Supplier.")
                    .build();
        } else if (candidates.size() == 1) {
            CandidateMatch<Party> pMatch = candidates.get(0);
            Party matched = pMatch.entity;
            return RoleMatchPreviewResponse.builder()
                    .targetRole("PARTY")
                    .resolution("REUSE_EXISTING")
                    .matchedBy(pMatch.matchType)
                    .existingEntityId(matched.getId())
                    .existingEntityName(matched.getName())
                    .existingEntityPhone(matched.getPhone())
                    .existingEntityGstin(matched.getGstin())
                    .existingEntityEmail(matched.getEmail())
                    .existingEntityAddress(matched.getAddress())
                    .gstinMismatch(pMatch.isGstinMismatch())
                    .message("Existing unlinked Supplier #" + matched.getId() + " (" + matched.getName() + ") matched by " + pMatch.matchType + (pMatch.isGstinMismatch() ? " (Note: GSTIN differs)" : "") + ". Require explicit confirmation before linking.")
                    .build();
        }

        return RoleMatchPreviewResponse.builder()
                .targetRole("PARTY")
                .resolution("CREATE_NEW")
                .matchedBy("NONE")
                .message("No matching unlinked Supplier found. A new Supplier profile will be created and linked.")
                .build();
    }

    private static class CandidateMatch<T> {
        final T entity;
        final String matchType;
        final boolean gstinMismatch;

        CandidateMatch(T entity, String matchType, boolean gstinMismatch) {
            this.entity = entity;
            this.matchType = matchType;
            this.gstinMismatch = gstinMismatch;
        }

        public boolean isGstinMismatch() {
            return gstinMismatch;
        }
    }

    private List<CandidateMatch<Customer>> findCandidateCustomers(Long firmId, String gstin, String phone, String name) {
        Map<Long, CandidateMatch<Customer>> matches = new LinkedHashMap<>();

        if (hasText(gstin)) {
            List<Customer> byGstin = customerRepo.findByFirmIdAndGstinIgnoreCase(firmId, gstin.trim());
            if (byGstin != null) {
                for (Customer c : byGstin) {
                    if (c != null && !isCustomerLinked(firmId, c.getId())) {
                        matches.put(c.getId(), new CandidateMatch<>(c, "GSTIN", false));
                    }
                }
            }
        }
        if (hasText(phone)) {
            List<Customer> byPhone = customerRepo.findByFirmIdAndPhone(firmId, phone.trim());
            if (byPhone != null) {
                for (Customer c : byPhone) {
                    if (c != null && !isCustomerLinked(firmId, c.getId())) {
                        boolean mismatch = hasText(gstin) && hasText(c.getGstin()) && !gstin.trim().equalsIgnoreCase(c.getGstin().trim());
                        matches.putIfAbsent(c.getId(), new CandidateMatch<>(c, "PHONE", mismatch));
                    }
                }
            }
        }
        if (hasText(name) && matches.isEmpty()) {
            List<Customer> byName = customerRepo.findByFirmIdAndNameIgnoreCase(firmId, name.trim());
            if (byName != null) {
                for (Customer c : byName) {
                    if (c != null && !isCustomerLinked(firmId, c.getId())) {
                        boolean mismatch = hasText(gstin) && hasText(c.getGstin()) && !gstin.trim().equalsIgnoreCase(c.getGstin().trim());
                        matches.putIfAbsent(c.getId(), new CandidateMatch<>(c, "NAME", mismatch));
                    }
                }
            }
        }
        return new ArrayList<>(matches.values());
    }

    private List<CandidateMatch<Party>> findCandidateParties(Long firmId, String gstin, String phone, String name) {
        Map<Long, CandidateMatch<Party>> matches = new LinkedHashMap<>();

        if (hasText(gstin)) {
            Optional<Party> opt = partyRepo.findFirstByFirmIdAndGstinIgnoreCase(firmId, gstin.trim());
            if (opt != null && opt.isPresent() && !isPartyLinked(firmId, opt.get().getId())) {
                matches.put(opt.get().getId(), new CandidateMatch<>(opt.get(), "GSTIN", false));
            }
        }
        if (hasText(phone)) {
            Optional<Party> opt = partyRepo.findFirstByFirmIdAndPhone(firmId, phone.trim());
            if (opt != null && opt.isPresent() && !isPartyLinked(firmId, opt.get().getId())) {
                boolean mismatch = hasText(gstin) && hasText(opt.get().getGstin()) && !gstin.trim().equalsIgnoreCase(opt.get().getGstin().trim());
                matches.putIfAbsent(opt.get().getId(), new CandidateMatch<>(opt.get(), "PHONE", mismatch));
            }
        }
        if (hasText(name) && matches.isEmpty()) {
            Optional<Party> opt = partyRepo.findFirstByFirmIdAndNameIgnoreCase(firmId, name.trim());
            if (opt != null && opt.isPresent() && !isPartyLinked(firmId, opt.get().getId())) {
                boolean mismatch = hasText(gstin) && hasText(opt.get().getGstin()) && !gstin.trim().equalsIgnoreCase(opt.get().getGstin().trim());
                matches.putIfAbsent(opt.get().getId(), new CandidateMatch<>(opt.get(), "NAME", mismatch));
            }
        }
        return new ArrayList<>(matches.values());
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse disableRole(Long linkId, RoleLifecycleRequest request) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required to disable a role");
        }
        String role = normalizeRole(request != null ? request.getRole() : null);
        Long firmId = resolveFirmId();

        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));
        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }

        Customer customer = customerRepo.findByIdAndFirmId(link.getCustomerId(), firmId).orElse(null);
        Party party = partyRepo.findByIdAndFirmId(link.getPartyId(), firmId).orElse(null);

        if ("CUSTOMER".equals(role)) {
            if (Boolean.FALSE.equals(link.getCustomerRoleActive())) {
                throw new IllegalStateException("Customer role is already disabled for this link");
            }
            CustomerBalanceSummary bal = customer != null
                    ? customerBalanceService.getCustomerBalance(firmId, customer.getId())
                    : customerBalanceService.getCustomerBalance(firmId, link.getCustomerId());
            if (bal != null && bal.getClosingBalanceDouble() != null && Math.abs(bal.getClosingBalanceDouble()) > 0.01) {
                double amt = Math.abs(bal.getClosingBalanceDouble());
                String kind = bal.getClosingBalanceDouble() > 0 ? "outstanding receivable" : "unadjusted customer advance";
                String cname = customer != null ? customer.getName() : ("Customer #" + link.getCustomerId());
                throw new IllegalStateException(String.format(
                        "Cannot disable Customer role for %s: unsettled %s of ₹%.2f exists. Settle invoices or credits first.",
                        cname, kind, amt));
            }
            link.setCustomerRoleActive(Boolean.FALSE);
        } else {
            if (Boolean.FALSE.equals(link.getPartyRoleActive())) {
                throw new IllegalStateException("Supplier role is already disabled for this link");
            }
            PartyFinancialSummary fin = partyService.getFinancialSummary(link.getPartyId(), firmId);
            String pname = party != null ? party.getName() : ("Supplier #" + link.getPartyId());
            if (fin != null && fin.getPendingPurchaseOrders() != null && fin.getPendingPurchaseOrders() > 0) {
                throw new IllegalStateException(String.format(
                        "Cannot disable Supplier role for %s: %d pending or issued Purchase Order(s) remain active.",
                        pname, fin.getPendingPurchaseOrders()));
            }
            if (fin != null && fin.getNetBalance() != null && Math.abs(fin.getNetBalance().doubleValue()) > 0.01) {
                double amt = Math.abs(fin.getNetBalance().doubleValue());
                String kind = fin.getNetBalance().doubleValue() > 0 ? "outstanding payable" : "unadjusted supplier advance";
                throw new IllegalStateException(String.format(
                        "Cannot disable Supplier role for %s: unsettled %s of ₹%.2f exists. Settle payables or advances first.",
                        pname, kind, amt));
            }
            link.setPartyRoleActive(Boolean.FALSE);
        }

        boolean custActive = !Boolean.FALSE.equals(link.getCustomerRoleActive());
        boolean partyActive = !Boolean.FALSE.equals(link.getPartyRoleActive());
        link.setStatus(computeLinkStatus(custActive, partyActive));
        link.setDisabledReason(request != null && hasText(request.getReason()) ? request.getReason().trim() : null);
        link.setStatusUpdatedAt(LocalDateTime.now());

        BusinessPartyLink saved = linkRepo.save(link);
        recordAuditEvent(firmId, saved.getId(), saved.getRelationshipVersion(), saved.getCustomerId(), saved.getPartyId(), "ROLE_DEACTIVATED", null, role + "_ENABLED", saved.getStatus(), link.getDisabledReason());
        return buildResponse(saved, customer, party, firmId);
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse reenableRole(Long linkId, RoleLifecycleRequest request) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required to re-enable a role");
        }
        String role = normalizeRole(request != null ? request.getRole() : null);
        Long firmId = resolveFirmId();

        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));
        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }

        if ("CUSTOMER".equals(role)) {
            link.setCustomerRoleActive(Boolean.TRUE);
        } else {
            link.setPartyRoleActive(Boolean.TRUE);
        }

        boolean custActive = !Boolean.FALSE.equals(link.getCustomerRoleActive());
        boolean partyActive = !Boolean.FALSE.equals(link.getPartyRoleActive());
        link.setStatus(computeLinkStatus(custActive, partyActive));
        if (custActive && partyActive) {
            link.setDisabledReason(null);
        }
        link.setStatusUpdatedAt(LocalDateTime.now());

        BusinessPartyLink saved = linkRepo.save(link);
        Customer customer = customerRepo.findByIdAndFirmId(saved.getCustomerId(), firmId).orElse(null);
        Party party = partyRepo.findByIdAndFirmId(saved.getPartyId(), firmId).orElse(null);
        recordAuditEvent(firmId, saved.getId(), saved.getRelationshipVersion(), saved.getCustomerId(), saved.getPartyId(), "ROLE_REENABLED", null, null, saved.getStatus(), "Role re-enabled");
        return buildResponse(saved, customer, party, firmId);
    }

    private String normalizeRole(String role) {
        if (!hasText(role)) {
            throw new IllegalArgumentException("Role ('CUSTOMER' or 'PARTY') is required");
        }
        String r = role.trim().toUpperCase();
        if ("CUSTOMER".equals(r)) return "CUSTOMER";
        if ("PARTY".equals(r) || "SUPPLIER".equals(r)) return "PARTY";
        throw new IllegalArgumentException("Unsupported role: " + role + ". Must be 'CUSTOMER' or 'PARTY'");
    }

    private String computeLinkStatus(boolean customerActive, boolean partyActive) {
        if (customerActive && partyActive) return "ACTIVE";
        if (!customerActive && !partyActive) return "BOTH_DISABLED";
        return customerActive ? "PARTY_DISABLED" : "CUSTOMER_DISABLED";
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse syncProfiles(Long linkId, SyncProfileRequest request) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required for profile sync");
        }
        if (request == null || !hasText(request.getSource())) {
            throw new IllegalArgumentException("Sync source ('CUSTOMER' or 'PARTY') is required");
        }

        Long firmId = resolveFirmId();
        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));

        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }

        Customer customer = customerRepo.findByIdAndFirmId(link.getCustomerId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + link.getCustomerId() + " not found"));
        Party party = partyRepo.findByIdAndFirmId(link.getPartyId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier #" + link.getPartyId() + " not found"));

        String source = request.getSource().trim().toUpperCase();
        if ("CUSTOMER".equals(source)) {
            // Push common fields from Customer into Party (never copy role-specific finances)
            String name = hasText(request.getName()) ? request.getName().trim() : customer.getName();
            String phone = hasText(request.getPhone()) ? request.getPhone().trim() : customer.getPhone();
            String email = hasText(request.getEmail()) ? request.getEmail().trim() : customer.getEmail();
            String address = hasText(request.getAddress()) ? request.getAddress().trim() : customer.getAddress();
            String gstin = hasText(request.getGstin()) ? request.getGstin().trim() : customer.getGstin();

            if (hasText(name) && !name.equalsIgnoreCase(party.getName())) {
                recordAliasInternal(firmId, link.getId(), customer.getId(), party.getId(), party.getName(), "PREVIOUS_NAME", "PARTY", "Sync from Customer");
                party.setName(name);
            }
            if (phone != null) party.setPhone(phone);
            if (email != null) party.setEmail(email);
            if (address != null) party.setAddress(address);
            if (gstin != null) party.setGstin(gstin);

            party = partyRepo.save(party);
        } else if ("PARTY".equals(source)) {
            // Push common fields from Party into Customer
            String name = hasText(request.getName()) ? request.getName().trim() : party.getName();
            String phone = hasText(request.getPhone()) ? request.getPhone().trim() : party.getPhone();
            String email = hasText(request.getEmail()) ? request.getEmail().trim() : party.getEmail();
            String address = hasText(request.getAddress()) ? request.getAddress().trim() : party.getAddress();
            String gstin = hasText(request.getGstin()) ? request.getGstin().trim() : party.getGstin();

            if (hasText(name) && !name.equalsIgnoreCase(customer.getName())) {
                recordAliasInternal(firmId, link.getId(), customer.getId(), party.getId(), customer.getName(), "PREVIOUS_NAME", "CUSTOMER", "Sync from Supplier");
                customer.setName(name);
            }
            if (phone != null) customer.setPhone(phone);
            if (email != null) customer.setEmail(email);
            if (address != null) customer.setAddress(address);
            if (gstin != null) customer.setGstin(gstin);

            customer = customerRepo.save(customer);
        } else {
            throw new IllegalArgumentException("Unknown sync source: " + source + ". Must be 'CUSTOMER' or 'PARTY'");
        }

        recordAuditEvent(firmId, link.getId(), link.getRelationshipVersion(), customer.getId(), party.getId(), "PROFILES_SYNCED", null, null, null, "Profile fields synchronized from " + source);

        return buildResponse(link, customer, party, firmId);
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse updateCanonicalName(Long linkId, CanonicalNameUpdateRequest request) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required to update canonical name");
        }
        if (request == null || !hasText(request.getCanonicalName())) {
            throw new IllegalArgumentException("New canonical name is required and cannot be empty");
        }

        Long firmId = resolveFirmId();
        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));

        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }

        if (!isActivelyLinked(link)) {
            throw new IllegalStateException("Cannot update canonical name on an unlinked identity relationship");
        }

        Customer customer = customerRepo.findByIdAndFirmId(link.getCustomerId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + link.getCustomerId() + " not found"));
        Party party = partyRepo.findByIdAndFirmId(link.getPartyId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier #" + link.getPartyId() + " not found"));

        String newName = request.getCanonicalName().trim();
        String oldName = customer.getName();

        if (oldName.equalsIgnoreCase(newName)) {
            return buildResponse(link, customer, party, firmId);
        }

        // Check alias and name conflicts in current firm
        String normalizedNew = BusinessIdentityAlias.normalize(newName);
        List<BusinessIdentityAlias> activeAliases = aliasRepo.findByFirmIdAndNormalizedValueAndActiveTrue(firmId, normalizedNew);
        for (BusinessIdentityAlias a : activeAliases) {
            if (a.getLinkId() != null && !a.getLinkId().equals(linkId)) {
                throw new IllegalArgumentException("Name conflict: '" + newName + "' is already registered as an active alias for another business identity in this firm.");
            }
        }

        // Preserve old canonical name as a searchable alias
        recordAliasInternal(firmId, link.getId(), customer.getId(), party.getId(), oldName, "PREVIOUS_NAME", "SHARED", "Preserved previous canonical name after rename to " + newName);

        // Atomically update both records
        customer.setName(newName);
        customerRepo.save(customer);

        party.setName(newName);
        partyRepo.save(party);

        recordAuditEvent(firmId, link.getId(), link.getRelationshipVersion(), customer.getId(), party.getId(), "CANONICAL_NAME_CHANGED", request.getUpdatedBy(), oldName, newName, request.getReason());

        return buildResponse(link, customer, party, firmId);
    }

    @Override
    @Transactional
    public BusinessPartyLinkResponse softUnlink(Long linkId, UnlinkRequest request) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required to unlink");
        }

        Long firmId = resolveFirmId();
        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));

        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }

        // Administrator PIN Verification (Firm-Scoped)
        String configKey = "ADMIN_PIN_" + firmId;
        Optional<AppConfig> pinOpt = appConfigRepo.findById(configKey);
        if (pinOpt.isEmpty() || !hasText(pinOpt.get().getConfigValue())) {
            throw new AdminAuthorizationException("Administrator PIN is not configured for firm #" + firmId + ". Configure ADMIN_PIN before unlinking.");
        }
        String expectedPin = pinOpt.get().getConfigValue().trim();
        if (request == null || !hasText(request.getAdminPin()) || !request.getAdminPin().trim().equals(expectedPin)) {
            throw new AdminAuthorizationException("Invalid Administrator PIN. Unlink authorization rejected.");
        }

        // Shared Aliases Ownership Resolution
        List<BusinessIdentityAlias> aliases = aliasRepo.findByFirmIdAndLinkIdAndActiveTrue(firmId, linkId);
        Map<Long, String> resolutions = request.getAliasResolutions() != null ? request.getAliasResolutions() : Collections.emptyMap();
        for (BusinessIdentityAlias alias : aliases) {
            if ("SHARED".equalsIgnoreCase(alias.getOwnerRole())) {
                String outcome = resolutions.get(alias.getId());
                if (!hasText(outcome)) {
                    throw new IllegalStateException("Shared alias #" + alias.getId() + " ('" + alias.getAliasValue() + "') must have an explicit resolution (ASSIGN_CUSTOMER, ASSIGN_PARTY, or RETIRE) prior to unlinking.");
                }
                String normalizedOutcome = outcome.trim().toUpperCase();
                if ("RETIRE".equals(normalizedOutcome)) {
                    alias.setActive(Boolean.FALSE);
                    alias.setOwnerRole("RETIRED");
                } else if ("ASSIGN_CUSTOMER".equals(normalizedOutcome)) {
                    alias.setOwnerRole("CUSTOMER");
                } else if ("ASSIGN_PARTY".equals(normalizedOutcome)) {
                    alias.setOwnerRole("PARTY");
                } else {
                    throw new IllegalArgumentException("Unsupported alias resolution: " + outcome + ". Must be 'ASSIGN_CUSTOMER', 'ASSIGN_PARTY', or 'RETIRE'");
                }
                aliasRepo.save(alias);
                recordAuditEvent(firmId, link.getId(), link.getRelationshipVersion(), link.getCustomerId(), link.getPartyId(), "ALIAS_RESOLVED", request.getUnlinkedBy(), "SHARED", alias.getOwnerRole(), "Resolved alias during unlink: " + alias.getAliasValue());
            }
        }

        // Controlled soft unlinking: Nullify active foreign key pointers so historical row is retained without unique index collision
        link.setStatus("UNLINKED");
        link.setActiveCustomerId(null);
        link.setActivePartyId(null);
        link.setUnlinkedAt(LocalDateTime.now());
        link.setUnlinkedBy(hasText(request.getUnlinkedBy()) ? request.getUnlinkedBy().trim() : "Administrator");
        link.setUnlinkReason(hasText(request.getReason()) ? request.getReason().trim() : null);
        link.setStatusUpdatedAt(LocalDateTime.now());

        BusinessPartyLink saved = linkRepo.save(link);

        Customer customer = customerRepo.findByIdAndFirmId(saved.getCustomerId(), firmId).orElse(null);
        Party party = partyRepo.findByIdAndFirmId(saved.getPartyId(), firmId).orElse(null);

        recordAuditEvent(firmId, saved.getId(), saved.getRelationshipVersion(), saved.getCustomerId(), saved.getPartyId(), "UNLINK_COMPLETED", link.getUnlinkedBy(), "ACTIVE", "UNLINKED", link.getUnlinkReason());

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
                .orElseThrow(() -> new IllegalArgumentException("No link found between Customer #" + customerId + " and Supplier #" + partyId + " in current firm"));

        link.setStatus("UNLINKED");
        link.setActiveCustomerId(null);
        link.setActivePartyId(null);
        link.setUnlinkedAt(LocalDateTime.now());
        link.setUnlinkedBy("System / Legacy Delete");
        link.setStatusUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);

        recordAuditEvent(firmId, link.getId(), link.getRelationshipVersion(), customerId, partyId, "UNLINK_COMPLETED", "Legacy Delete", "ACTIVE", "UNLINKED", "Legacy soft unlink");
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
        link.setStatus("UNLINKED");
        link.setActiveCustomerId(null);
        link.setActivePartyId(null);
        link.setUnlinkedAt(LocalDateTime.now());
        link.setUnlinkedBy("System / Legacy Delete");
        link.setStatusUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);

        recordAuditEvent(firmId, link.getId(), link.getRelationshipVersion(), link.getCustomerId(), link.getPartyId(), "UNLINK_COMPLETED", "Legacy Delete", "ACTIVE", "UNLINKED", "Legacy soft unlink");
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessPartyLinkResponse getLinkedPartyForCustomer(Long customerId) {
        if (customerId == null) return null;
        Long firmId = resolveFirmId();

        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in current firm"));

        return linkRepo.findByFirmIdAndActiveCustomerId(firmId, customerId)
                .or(() -> linkRepo.findByFirmIdAndCustomerId(firmId, customerId).filter(this::isActivelyLinked))
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

        return linkRepo.findByFirmIdAndActivePartyId(firmId, partyId)
                .or(() -> linkRepo.findByFirmIdAndPartyId(firmId, partyId).filter(this::isActivelyLinked))
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

        List<BusinessPartyLink> activeLinks = linkRepo.findByFirmId(firmId).stream()
                .filter(this::isActivelyLinked)
                .collect(Collectors.toList());

        if ("CUSTOMER".equalsIgnoreCase(type)) {
            Set<Long> linkedCustomerIds = activeLinks.stream()
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
            Set<Long> linkedPartyIds = activeLinks.stream()
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
    public List<UnifiedCounterpartySearchResult> searchUnified(String targetRoleParam, String query, Integer limitParam) {
        Long firmId = resolveFirmId();
        String targetRole = hasText(targetRoleParam) ? targetRoleParam.trim().toUpperCase() : "CUSTOMER";
        String q = (query != null) ? query.trim() : "";
        int maxResults = (limitParam != null && limitParam > 0) ? Math.min(limitParam, 50) : 25;

        // Fetch active links in firm
        List<BusinessPartyLink> activeLinks = linkRepo.findByFirmId(firmId).stream()
                .filter(this::isActivelyLinked)
                .collect(Collectors.toList());

        Map<Long, BusinessPartyLink> custIdToLink = new HashMap<>();
        Map<Long, BusinessPartyLink> partyIdToLink = new HashMap<>();
        for (BusinessPartyLink link : activeLinks) {
            custIdToLink.put(link.getCustomerId(), link);
            partyIdToLink.put(link.getPartyId(), link);
        }

        // Match customers using DB query
        List<Customer> matchedCustomers = hasText(q)
                ? customerRepo.searchByFirmIdAndQuery(firmId, q)
                : customerRepo.findByFirmIdOrderByNameAsc(firmId);

        // Match parties using DB query
        List<Party> matchedParties = hasText(q)
                ? partyRepo.searchByFirmIdAndQuery(firmId, q)
                : partyRepo.findByFirmIdOrderByNameAsc(firmId);

        // Match active aliases
        List<BusinessIdentityAlias> matchedAliases = hasText(q)
                ? aliasRepo.searchActiveAliases(firmId, q)
                : Collections.emptyList();

        Map<String, UnifiedCounterpartySearchResult> resultsByKey = new LinkedHashMap<>();

        // Helper to register linked identity once
        java.util.function.BiConsumer<Customer, Party> registerLinked = (c, p) -> {
            if (c == null || p == null) return;
            String key = "LINK_" + c.getId() + "_" + p.getId();
            if (!resultsByKey.containsKey(key)) {
                BusinessPartyLink link = custIdToLink.get(c.getId());
                boolean custRole = link != null ? !Boolean.FALSE.equals(link.getCustomerRoleActive()) : true;
                boolean partyRole = link != null ? !Boolean.FALSE.equals(link.getPartyRoleActive()) : true;
                List<String> roles = new ArrayList<>();
                if (custRole) roles.add("CUSTOMER");
                if (partyRole) roles.add("PARTY");

                String action = "CUSTOMER".equals(targetRole) ? "SELECT_CUSTOMER" : "SELECT_PARTY";
                resultsByKey.put(key, UnifiedCounterpartySearchResult.builder()
                        .resultType("LINKED_BUSINESS")
                        .customerId(c.getId())
                        .partyId(p.getId())
                        .linkId(link != null ? link.getId() : null)
                        .canonicalName(c.getName())
                        .phone(hasText(c.getPhone()) ? c.getPhone() : p.getPhone())
                        .email(hasText(c.getEmail()) ? c.getEmail() : p.getEmail())
                        .gstin(hasText(c.getGstin()) ? c.getGstin() : p.getGstin())
                        .address(hasText(c.getAddress()) ? c.getAddress() : p.getAddress())
                        .availableRoles(roles)
                        .linkStatus(link != null ? link.getStatus() : "ACTIVE")
                        .matchReason("LINKED_BUSINESS")
                        .availableAction(action)
                        .build());
            }
        };

        // 1. Process Customers
        for (Customer c : matchedCustomers) {
            BusinessPartyLink link = custIdToLink.get(c.getId());
            if (link != null) {
                Party p = partyRepo.findByIdAndFirmId(link.getPartyId(), firmId).orElse(null);
                registerLinked.accept(c, p);
            } else {
                String key = "CUST_" + c.getId();
                if (!resultsByKey.containsKey(key)) {
                    String action = "CUSTOMER".equals(targetRole) ? "SELECT_CUSTOMER" : "ACTIVATE_PARTY_ROLE";
                    resultsByKey.put(key, UnifiedCounterpartySearchResult.builder()
                            .resultType("CUSTOMER".equals(targetRole) ? "EXISTING_CUSTOMER" : "CUSTOMER_ONLY")
                            .customerId(c.getId())
                            .canonicalName(c.getName())
                            .phone(c.getPhone())
                            .email(c.getEmail())
                            .gstin(c.getGstin())
                            .address(c.getAddress())
                            .availableRoles(List.of("CUSTOMER"))
                            .linkStatus("NONE")
                            .matchReason("NAME")
                            .availableAction(action)
                            .build());
                }
            }
        }

        // 2. Process Parties
        for (Party p : matchedParties) {
            BusinessPartyLink link = partyIdToLink.get(p.getId());
            if (link != null) {
                Customer c = customerRepo.findByIdAndFirmId(link.getCustomerId(), firmId).orElse(null);
                registerLinked.accept(c, p);
            } else {
                String key = "PARTY_" + p.getId();
                if (!resultsByKey.containsKey(key)) {
                    String action = "PARTY".equals(targetRole) ? "SELECT_PARTY" : "ACTIVATE_CUSTOMER_ROLE";
                    resultsByKey.put(key, UnifiedCounterpartySearchResult.builder()
                            .resultType("PARTY".equals(targetRole) ? "EXISTING_PARTY" : "PARTY_ONLY")
                            .partyId(p.getId())
                            .canonicalName(p.getName())
                            .phone(p.getPhone())
                            .email(p.getEmail())
                            .gstin(p.getGstin())
                            .address(p.getAddress())
                            .availableRoles(List.of("PARTY"))
                            .linkStatus("NONE")
                            .matchReason("NAME")
                            .availableAction(action)
                            .build());
                }
            }
        }

        // 3. Process Alias matches
        for (BusinessIdentityAlias a : matchedAliases) {
            if (a.getLinkId() != null) {
                BusinessPartyLink link = linkRepo.findById(a.getLinkId()).orElse(null);
                if (link != null && isActivelyLinked(link)) {
                    Customer c = customerRepo.findByIdAndFirmId(link.getCustomerId(), firmId).orElse(null);
                    Party p = partyRepo.findByIdAndFirmId(link.getPartyId(), firmId).orElse(null);
                    String key = "LINK_" + link.getCustomerId() + "_" + link.getPartyId();
                    if (!resultsByKey.containsKey(key) && c != null && p != null) {
                        registerLinked.accept(c, p);
                    }
                    if (resultsByKey.containsKey(key)) {
                        resultsByKey.get(key).setMatchedAlias(a.getAliasValue());
                        resultsByKey.get(key).setMatchReason("ALIAS: " + a.getAliasValue());
                    }
                }
            } else if (a.getCustomerId() != null) {
                Customer c = customerRepo.findByIdAndFirmId(a.getCustomerId(), firmId).orElse(null);
                if (c != null) {
                    String key = "CUST_" + c.getId();
                    if (!resultsByKey.containsKey(key)) {
                        String action = "CUSTOMER".equals(targetRole) ? "SELECT_CUSTOMER" : "ACTIVATE_PARTY_ROLE";
                        resultsByKey.put(key, UnifiedCounterpartySearchResult.builder()
                                .resultType("CUSTOMER".equals(targetRole) ? "EXISTING_CUSTOMER" : "CUSTOMER_ONLY")
                                .customerId(c.getId())
                                .canonicalName(c.getName())
                                .phone(c.getPhone())
                                .email(c.getEmail())
                                .gstin(c.getGstin())
                                .address(c.getAddress())
                                .availableRoles(List.of("CUSTOMER"))
                                .linkStatus("NONE")
                                .matchedAlias(a.getAliasValue())
                                .matchReason("ALIAS: " + a.getAliasValue())
                                .availableAction(action)
                                .build());
                    }
                }
            } else if (a.getPartyId() != null) {
                Party p = partyRepo.findByIdAndFirmId(a.getPartyId(), firmId).orElse(null);
                if (p != null) {
                    String key = "PARTY_" + p.getId();
                    if (!resultsByKey.containsKey(key)) {
                        String action = "PARTY".equals(targetRole) ? "SELECT_PARTY" : "ACTIVATE_CUSTOMER_ROLE";
                        resultsByKey.put(key, UnifiedCounterpartySearchResult.builder()
                                .resultType("PARTY".equals(targetRole) ? "EXISTING_PARTY" : "PARTY_ONLY")
                                .partyId(p.getId())
                                .canonicalName(p.getName())
                                .phone(p.getPhone())
                                .email(p.getEmail())
                                .gstin(p.getGstin())
                                .address(p.getAddress())
                                .availableRoles(List.of("PARTY"))
                                .linkStatus("NONE")
                                .matchedAlias(a.getAliasValue())
                                .matchReason("ALIAS: " + a.getAliasValue())
                                .availableAction(action)
                                .build());
                    }
                }
            }
        }

        return resultsByKey.values().stream().limit(maxResults).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AliasDto addAlias(Long linkId, CreateAliasRequest request) {
        if (linkId == null) {
            throw new IllegalArgumentException("Link ID is required to add an alias");
        }
        if (request == null || !hasText(request.getAliasValue())) {
            throw new IllegalArgumentException("Alias value is required");
        }

        Long firmId = resolveFirmId();
        BusinessPartyLink link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link #" + linkId + " not found"));
        if (!firmId.equals(link.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }

        String rawAlias = request.getAliasValue().trim();
        String norm = BusinessIdentityAlias.normalize(rawAlias);

        // Verify conflict with active aliases in firm
        List<BusinessIdentityAlias> existingAliases = aliasRepo.findByFirmIdAndNormalizedValueAndActiveTrue(firmId, norm);
        for (BusinessIdentityAlias a : existingAliases) {
            if (a.getLinkId() != null && !a.getLinkId().equals(linkId)) {
                throw new IllegalArgumentException("Alias conflict: '" + rawAlias + "' is already assigned to another business identity in this firm.");
            }
        }

        BusinessIdentityAlias entity = BusinessIdentityAlias.builder()
                .firmId(firmId)
                .linkId(link.getId())
                .customerId(link.getCustomerId())
                .partyId(link.getPartyId())
                .aliasValue(rawAlias)
                .normalizedValue(norm)
                .aliasType(hasText(request.getAliasType()) ? request.getAliasType().trim().toUpperCase() : "MANUAL_ALIAS")
                .ownerRole(hasText(request.getOwnerRole()) ? request.getOwnerRole().trim().toUpperCase() : "SHARED")
                .active(Boolean.TRUE)
                .createdAt(LocalDateTime.now())
                .createdBy(hasText(request.getCreatedBy()) ? request.getCreatedBy().trim() : "User")
                .resolutionNotes(request.getNotes())
                .build();

        BusinessIdentityAlias saved = aliasRepo.save(entity);

        recordAuditEvent(firmId, link.getId(), link.getRelationshipVersion(), link.getCustomerId(), link.getPartyId(), "ALIAS_ADDED", entity.getCreatedBy(), null, rawAlias, "Added alias: " + rawAlias);

        return toAliasDto(saved);
    }

    @Override
    @Transactional
    public void removeAlias(Long linkId, Long aliasId, String reason, String actor) {
        if (aliasId == null) {
            throw new IllegalArgumentException("Alias ID is required");
        }
        Long firmId = resolveFirmId();
        BusinessIdentityAlias alias = aliasRepo.findById(aliasId)
                .orElseThrow(() -> new IllegalArgumentException("Alias #" + aliasId + " not found"));
        if (!firmId.equals(alias.getFirmId())) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        alias.setActive(Boolean.FALSE);
        aliasRepo.save(alias);

        recordAuditEvent(firmId, linkId, null, alias.getCustomerId(), alias.getPartyId(), "ALIAS_REMOVED", actor, alias.getAliasValue(), null, reason != null ? reason : "Removed alias");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AliasDto> getAliases(Long linkId) {
        if (linkId == null) return Collections.emptyList();
        Long firmId = resolveFirmId();
        return aliasRepo.findByFirmIdAndLinkIdAndActiveTrue(firmId, linkId).stream()
                .map(this::toAliasDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditTrailEventDto> getAuditEvents(Long linkId) {
        if (linkId == null) return Collections.emptyList();
        Long firmId = resolveFirmId();
        return eventRepo.findByFirmIdAndLinkIdOrderByTimestampDesc(firmId, linkId).stream()
                .map(e -> AuditTrailEventDto.builder()
                        .id(e.getId())
                        .firmId(e.getFirmId())
                        .linkId(e.getLinkId())
                        .relationshipVersion(e.getRelationshipVersion())
                        .customerId(e.getCustomerId())
                        .partyId(e.getPartyId())
                        .action(e.getAction())
                        .actor(e.getActor())
                        .timestamp(e.getTimestamp())
                        .beforeValue(e.getBeforeValue())
                        .afterValue(e.getAfterValue())
                        .reason(e.getReason())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAdminPinConfigured(Long firmIdParam) {
        Long firmId = firmIdParam != null ? firmIdParam : resolveFirmId();
        return appConfigRepo.findById("ADMIN_PIN_" + firmId)
                .map(c -> c.getConfigValue() != null && !c.getConfigValue().trim().isEmpty())
                .orElse(false);
    }

    @Override
    @Transactional
    public void setAdminPin(Long firmIdParam, AdminPinSetRequest request) {
        Long firmId = firmIdParam != null ? firmIdParam : resolveFirmId();
        if (request == null || !hasText(request.getNewPin()) || request.getNewPin().trim().length() < 4) {
            throw new IllegalArgumentException("Administrator PIN must be at least 4 digits");
        }
        String configKey = "ADMIN_PIN_" + firmId;
        Optional<AppConfig> existingOpt = appConfigRepo.findById(configKey);
        if (existingOpt.isPresent() && hasText(existingOpt.get().getConfigValue())) {
            String currentPin = existingOpt.get().getConfigValue().trim();
            if (request.getOldPin() == null || !request.getOldPin().trim().equals(currentPin)) {
                throw new AdminAuthorizationException("Current Administrator PIN is incorrect");
            }
        }
        AppConfig config = existingOpt.orElse(new AppConfig(configKey, ""));
        config.setConfigValue(request.getNewPin().trim());
        appConfigRepo.save(config);
    }

    private AliasDto toAliasDto(BusinessIdentityAlias a) {
        return AliasDto.builder()
                .id(a.getId())
                .firmId(a.getFirmId())
                .linkId(a.getLinkId())
                .customerId(a.getCustomerId())
                .partyId(a.getPartyId())
                .aliasValue(a.getAliasValue())
                .normalizedValue(a.getNormalizedValue())
                .aliasType(a.getAliasType())
                .ownerRole(a.getOwnerRole())
                .active(a.getActive())
                .createdAt(a.getCreatedAt())
                .createdBy(a.getCreatedBy())
                .resolutionNotes(a.getResolutionNotes())
                .build();
    }

    private void recordAliasInternal(Long firmId, Long linkId, Long customerId, Long partyId, String aliasValue, String aliasType, String ownerRole, String notes) {
        if (!hasText(aliasValue)) return;
        String norm = BusinessIdentityAlias.normalize(aliasValue);
        BusinessIdentityAlias alias = BusinessIdentityAlias.builder()
                .firmId(firmId)
                .linkId(linkId)
                .customerId(customerId)
                .partyId(partyId)
                .aliasValue(aliasValue.trim())
                .normalizedValue(norm)
                .aliasType(aliasType)
                .ownerRole(ownerRole)
                .active(Boolean.TRUE)
                .createdAt(LocalDateTime.now())
                .createdBy("System")
                .resolutionNotes(notes)
                .build();
        aliasRepo.save(alias);
    }

    private void recordAuditEvent(Long firmId, Long linkId, Integer version, Long customerId, Long partyId, String action, String actor, String beforeVal, String afterVal, String reason) {
        try {
            BusinessPartyLinkEvent event = BusinessPartyLinkEvent.builder()
                    .firmId(firmId)
                    .linkId(linkId)
                    .relationshipVersion(version)
                    .customerId(customerId)
                    .partyId(partyId)
                    .action(action)
                    .actor(hasText(actor) ? actor.trim() : "System")
                    .timestamp(LocalDateTime.now())
                    .beforeValue(beforeVal)
                    .afterValue(afterVal)
                    .reason(reason)
                    .build();
            eventRepo.save(event);
        } catch (Exception e) {
            System.err.println("Note: failed to record audit event: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFirmLinksMap(Long firmIdParam) {
        Long firmId = firmIdParam != null ? firmIdParam : resolveFirmId();
        List<BusinessPartyLink> links = linkRepo.findByFirmId(firmId).stream()
                .filter(this::isActivelyLinked)
                .collect(Collectors.toList());

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

                boolean custRoleActive = !Boolean.FALSE.equals(link.getCustomerRoleActive());
                boolean ptyRoleActive = !Boolean.FALSE.equals(link.getPartyRoleActive());
                String linkStatus = hasText(link.getStatus()) ? link.getStatus() : computeLinkStatus(custRoleActive, ptyRoleActive);

                Map<String, Object> pInfo = new HashMap<>();
                pInfo.put("partyId", pId);
                pInfo.put("partyName", p != null ? p.getName() : "Unknown Supplier");
                pInfo.put("partyPhone", p != null ? p.getPhone() : null);
                pInfo.put("partyEmail", p != null ? p.getEmail() : null);
                pInfo.put("partyGstin", p != null ? p.getGstin() : null);
                pInfo.put("partyAddress", p != null ? p.getAddress() : null);
                pInfo.put("linkId", link.getId());
                pInfo.put("status", linkStatus);
                pInfo.put("customerRoleActive", custRoleActive);
                pInfo.put("partyRoleActive", ptyRoleActive);
                customerToParty.put(cId, pInfo);

                Map<String, Object> cInfo = new HashMap<>();
                cInfo.put("customerId", cId);
                cInfo.put("customerName", c != null ? c.getName() : "Unknown Customer");
                cInfo.put("customerPhone", c != null ? c.getPhone() : null);
                cInfo.put("customerEmail", c != null ? c.getEmail() : null);
                cInfo.put("customerGstin", c != null ? c.getGstin() : null);
                cInfo.put("customerAddress", c != null ? c.getAddress() : null);
                cInfo.put("linkId", link.getId());
                cInfo.put("status", linkStatus);
                cInfo.put("customerRoleActive", custRoleActive);
                cInfo.put("partyRoleActive", ptyRoleActive);
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

        BusinessPartyLink link = linkRepo.findByFirmIdAndCustomerIdAndPartyId(firmId, customerId, partyId)
                .orElseThrow(() -> new IllegalArgumentException("No active link found between Customer #" + customerId + " and Supplier #" + partyId));

        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Customer #" + customerId + " not found in firm"));

        Party party = partyRepo.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier #" + partyId + " not found in firm"));

        LocalDate toDate = (to == null) ? LocalDate.now() : to;
        LocalDate fromDate = (from == null) ? LocalDate.of(1970, 1, 1) : from;

        CustomerStatementResponse custStatement = statementService.getCustomerStatement(firmId, customerId, fromDate, toDate);
        PartyStatementResponse ptyStatement = statementService.getPartyStatement(firmId, partyId, fromDate, toDate);

        double totalReceivables = custStatement.getClosingBalance() != null ? custStatement.getClosingBalance() : 0.0;
        double totalPayables = ptyStatement.getClosingBalance() != null ? ptyStatement.getClosingBalance() : 0.0;

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

        boolean custActive = !Boolean.FALSE.equals(link.getCustomerRoleActive());
        boolean ptyActive = !Boolean.FALSE.equals(link.getPartyRoleActive());
        String linkStatus = hasText(link.getStatus()) ? link.getStatus() : computeLinkStatus(custActive, ptyActive);
        String canonicalName = (customer != null && hasText(customer.getName()))
                ? customer.getName()
                : (party != null && hasText(party.getName()) ? party.getName() : null);

        return BusinessPartyLinkResponse.builder()
                .id(link.getId())
                .firmId(link.getFirmId())
                .customerId(link.getCustomerId())
                .partyId(link.getPartyId())
                .linkedAt(link.getLinkedAt())
                .linkedBy(link.getLinkedBy())
                .notes(link.getNotes())
                .canonicalName(canonicalName)
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .customerEmail(customer != null ? customer.getEmail() : null)
                .customerGstin(customer != null ? customer.getGstin() : null)
                .customerAddress(customer != null ? customer.getAddress() : null)
                .customerNetReceivable(receivable)
                .partyName(party != null ? party.getName() : null)
                .partyPhone(party != null ? party.getPhone() : null)
                .partyEmail(party != null ? party.getEmail() : null)
                .partyGstin(party != null ? party.getGstin() : null)
                .partyAddress(party != null ? party.getAddress() : null)
                .partyContactPerson(party != null ? party.getContactPerson() : null)
                .partyNetPayable(payable)
                .partyBalanceStatus(partyStatus)
                .status(linkStatus)
                .customerRoleActive(custActive)
                .partyRoleActive(ptyActive)
                .statusUpdatedAt(link.getStatusUpdatedAt())
                .disabledReason(link.getDisabledReason())
                .relationshipVersion(link.getRelationshipVersion() != null ? link.getRelationshipVersion() : 1)
                .unlinkedAt(link.getUnlinkedAt())
                .unlinkedBy(link.getUnlinkedBy())
                .unlinkReason(link.getUnlinkReason())
                .build();
    }
}
