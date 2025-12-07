package com.billing.simple.billsoft.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.repo.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) {
        this.repo = repo;
    }

    public Customer create(Long firmId, Customer customer) {
        customer.setId(null);
        customer.setFirmId(firmId);
        return repo.save(customer);
    }

    public List<Customer> getAll(Long firmId) {
        return repo.findAll()
                .stream()
                .filter(c -> c.getFirmId() != null && c.getFirmId().equals(firmId))
                .collect(Collectors.toList());
    }

    public Customer getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public Customer update(Long id, Customer updated) {
        Customer existing = repo.findById(id).orElse(null);
        if (existing == null)
            return null;

        existing.setName(updated.getName());
        existing.setPhone(updated.getPhone());
        existing.setEmail(updated.getEmail());
        existing.setAddress(updated.getAddress());
        existing.setGstin(updated.getGstin());

        return repo.save(existing);
    }

    public boolean delete(Long id) {
        if (!repo.existsById(id))
            return false;
        repo.deleteById(id);
        return true;
    }
}
