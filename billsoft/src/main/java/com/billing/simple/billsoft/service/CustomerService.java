package com.billing.simple.billsoft.service;

import java.util.List;

import org.springframework.stereotype.Service;

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
        return repo.save(customer);
    }

    public List<Customer> getAll(Long firmId) {
        return repo.findByFirmIdOrderByNameAsc(firmId);
    }

    public Customer getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer existing = repo.findById(id).orElse(null);
        if (existing == null)
            return null;

        if (request.getName() != null) existing.setName(request.getName().trim());
        existing.setPhone(request.getPhone());
        existing.setEmail(request.getEmail());
        existing.setAddress(request.getAddress());

        return repo.save(existing);
    }

    public boolean delete(Long id) {
        if (!repo.existsById(id))
            return false;
        repo.deleteById(id);
        return true;
    }
}