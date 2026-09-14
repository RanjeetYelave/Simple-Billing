package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.*;
import com.billing.simple.billsoft.assistant.repo.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Capability-Aware Tenant-Isolated Entity Disambiguator.
 * Queries dedicated read-only repositories and filters candidates strictly by capability type.
 */
@Component
public class OmnisearchEntityResolver {

    private final OmnisearchCustomerQueryRepository customerRepo;
    private final OmnisearchPartyQueryRepository partyRepo;
    private final OmnisearchProductQueryRepository productRepo;
    private final OmnisearchEmployeeQueryRepository employeeRepo;

    public OmnisearchEntityResolver(
            OmnisearchCustomerQueryRepository customerRepo,
            OmnisearchPartyQueryRepository partyRepo,
            OmnisearchProductQueryRepository productRepo,
            OmnisearchEmployeeQueryRepository employeeRepo
    ) {
        this.customerRepo = customerRepo;
        this.partyRepo = partyRepo;
        this.productRepo = productRepo;
        this.employeeRepo = employeeRepo;
    }

    public List<ResolvedEntityCandidate> resolve(Long firmId, String capabilityId, String rawToken) {
        List<ResolvedEntityCandidate> results = new ArrayList<>();
        if (firmId == null || rawToken == null || rawToken.isBlank()) {
            return results;
        }

        String query = rawToken.trim();

        // 1. Employee capabilities (attendance, salary, advance)
        if (capabilityId != null && (capabilityId.contains("EMPLOYEE") || capabilityId.contains("SALARY") || capabilityId.contains("ADVANCE"))) {
            List<OmnisearchEmployeeQueryRepository.EmployeeCandidateProjection> emps = employeeRepo.searchCandidates(firmId, query);
            for (var e : emps) {
                boolean exact = e.getName().equalsIgnoreCase(query) || e.getName().toLowerCase().startsWith(query.toLowerCase()) || query.toLowerCase().startsWith(e.getName().toLowerCase());
                results.add(ResolvedEntityCandidate.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .type(EntityType.EMPLOYEE)
                        .phone(e.getPhone())
                        .extraInfo("Role: " + (e.getRole() != null ? e.getRole() : "Staff"))
                        .matchScore(exact ? 1.0 : 0.8)
                        .exactMatch(exact)
                        .build());
            }
            return results;
        }

        // 2. Product / Stock capabilities
        if (capabilityId != null && (capabilityId.contains("STOCK") || capabilityId.contains("PRODUCT"))) {
            List<OmnisearchProductQueryRepository.ProductCandidateProjection> prods = productRepo.searchCandidates(firmId, query);
            for (var p : prods) {
                boolean exact = p.getName().equalsIgnoreCase(query) || p.getName().toLowerCase().startsWith(query.toLowerCase()) || query.toLowerCase().startsWith(p.getName().toLowerCase());
                results.add(ResolvedEntityCandidate.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .type(EntityType.PRODUCT)
                        .extraInfo("Stock: " + p.getStockQuantity())
                        .matchScore(exact ? 1.0 : 0.8)
                        .exactMatch(exact)
                        .build());
            }
            return results;
        }

        // 3. Party / Vendor capabilities
        if (capabilityId != null && capabilityId.contains("PARTY")) {
            List<OmnisearchPartyQueryRepository.PartyCandidateProjection> parties = partyRepo.searchCandidates(firmId, query);
            for (var p : parties) {
                boolean exact = p.getName().equalsIgnoreCase(query) || p.getName().toLowerCase().startsWith(query.toLowerCase()) || query.toLowerCase().startsWith(p.getName().toLowerCase());
                results.add(ResolvedEntityCandidate.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .type(EntityType.PARTY)
                        .phone(p.getPhone())
                        .extraInfo("Contact: " + (p.getContactPerson() != null ? p.getContactPerson() : "Vendor"))
                        .matchScore(exact ? 1.0 : 0.8)
                        .exactMatch(exact)
                        .build());
            }
            return results;
        }

        // 4. Customer capabilities & Default
        List<OmnisearchCustomerQueryRepository.CustomerCandidateProjection> customers = customerRepo.searchCandidates(firmId, query);
        for (var c : customers) {
            boolean exact = c.getName().equalsIgnoreCase(query) || c.getName().toLowerCase().startsWith(query.toLowerCase()) || query.toLowerCase().startsWith(c.getName().toLowerCase());
            results.add(ResolvedEntityCandidate.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .type(EntityType.CUSTOMER)
                    .phone(c.getPhone())
                    .extraInfo(c.getAddress() != null ? c.getAddress() : "")
                    .matchScore(exact ? 1.0 : 0.8)
                    .exactMatch(exact)
                    .build());
        }

        return results;
    }
}
