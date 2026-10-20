package com.billing.simple.billsoft.service;

import java.util.List;

import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.CustomerRequest;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.repo.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) {
        this.repo = repo;
    }

    public Customer create(Customer customer) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && customer.getFirmId() != null && !authoritativeFirmId.equals(customer.getFirmId())) {
            throw new TenantSecurityException("Cross-firm customer creation prohibited");
        }
        Long targetFirmId = authoritativeFirmId != null ? authoritativeFirmId : customer.getFirmId();
        if (targetFirmId != null) {
            customer.setFirmId(targetFirmId);
        }
        return repo.save(customer);
    }

    public List<Customer> getAll(Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long targetFirmId = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        if (targetFirmId == null) {
            return java.util.Collections.emptyList();
        }
        return repo.findByFirmIdOrderByNameAsc(targetFirmId);
    }

    public Customer getById(Long id) {
        Long fid = TenantContext.getCurrentFirmId();
        if (fid == null) {
            throw new TenantSecurityException("Active firm context is strictly required for this operation.");
        }
        return repo.findByIdAndFirmId(id, fid).orElse(null);
    }

    public Customer getById(Long id, Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long fid = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        if (fid == null) {
            throw new TenantSecurityException("Active firm context is strictly required for this operation.");
        }
        return repo.findByIdAndFirmId(id, fid).orElse(null);
    }

    @Transactional
    public Customer update(Long id, CustomerRequest request) {
        Long fid = TenantContext.getCurrentFirmId();
        if (fid == null) {
            throw new TenantSecurityException("Active firm context is strictly required for this operation.");
        }
        Customer existing = repo.findByIdAndFirmId(id, fid).orElse(null);
        if (existing == null)
            return null;

        if (request.getVersion() != null) {
            if (!request.getVersion().equals(existing.getVersion())) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Customer.class, id);
            }
            existing.setVersion(request.getVersion());
        }

        if (request.getName() != null)
            existing.setName(request.getName().trim());
        existing.setPhone(request.getPhone());
        existing.setEmail(request.getEmail());
        existing.setAddress(request.getAddress());
        if (request.getGstin() != null) {
            existing.setGstin(request.getGstin().trim());
        }

        return repo.save(existing);
    }

    @Transactional
    public boolean delete(Long id) {
        Long fid = TenantContext.getCurrentFirmId();
        if (fid == null) {
            throw new TenantSecurityException("Active firm context is strictly required for this operation.");
        }
        if (!repo.existsByIdAndFirmId(id, fid))
            return false;
        repo.deleteByIdAndFirmId(id, fid);
        return true;
    }
}