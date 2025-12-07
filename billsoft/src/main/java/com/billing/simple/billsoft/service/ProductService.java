package com.billing.simple.billsoft.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Product create(Long firmId, Product product) {
        product.setId(null);
        product.setFirmId(firmId);
        return repo.save(product);
    }

    public List<Product> getAll(Long firmId) {
        return repo.findAll()
                .stream()
                .filter(p -> p.getFirmId() != null && p.getFirmId().equals(firmId))
                .collect(Collectors.toList());
    }

    public Product getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Transactional
    public Product update(Long id, Product updated) {
        Optional<Product> opt = repo.findById(id);
        if (opt.isEmpty())
            return null;
        Product existing = opt.get();
        existing.setName(updated.getName());
        existing.setPrice(updated.getPrice());
        existing.setUnit(updated.getUnit());
        existing.setGstPercentage(updated.getGstPercentage());
        return repo.save(existing);
    }

    @Transactional
    public boolean delete(Long id) {
        if (!repo.existsById(id))
            return false;
        repo.deleteById(id);
        return true;
    }
}
