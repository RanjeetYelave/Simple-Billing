package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.dtos.CustomerRequest;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {

    @Mock
    private CustomerRepository repo;

    @InjectMocks
    private CustomerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCreate() {
        Customer customer = new Customer();
        customer.setName("John Doe");
        customer.setFirmId(1L);
        when(repo.save(customer)).thenReturn(customer);

        Customer result = service.create(customer);

        assertEquals("John Doe", result.getName());
        verify(repo, times(1)).save(customer);
    }

    @Test
    void testGetAll() {
        Long firmId = 1L;
        List<Customer> customers = Arrays.asList(new Customer(), new Customer());
        when(repo.findByFirmIdOrderByNameAsc(firmId)).thenReturn(customers);

        List<Customer> result = service.getAll(firmId);

        assertEquals(2, result.size());
        verify(repo, times(1)).findByFirmIdOrderByNameAsc(firmId);
    }

    @Test
    void testGetById() {
        Long id = 1L;
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFirmId(1L);
        when(repo.findByIdAndFirmId(id, 1L)).thenReturn(Optional.of(customer));

        Customer result = service.getById(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testUpdate() {
        Long id = 1L;
        Customer existing = new Customer();
        existing.setId(id);
        existing.setFirmId(1L);
        existing.setName("Old Name");

        when(repo.findByIdAndFirmId(id, 1L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        CustomerRequest req = new CustomerRequest();
        req.setName("New Name");
        Customer result = service.update(id, req);

        assertNotNull(result);
        assertEquals("New Name", result.getName());
    }

    @Test
    void testUpdateNotFound() {
        Long id = 1L;
        when(repo.findByIdAndFirmId(id, 1L)).thenReturn(Optional.empty());

        CustomerRequest req = new CustomerRequest();
        Customer result = service.update(id, req);

        assertNull(result);
    }

    @Test
    void testDelete() {
        Long id = 1L;
        when(repo.existsByIdAndFirmId(id, 1L)).thenReturn(true);

        boolean result = service.delete(id);

        assertTrue(result);
        verify(repo, times(1)).deleteByIdAndFirmId(id, 1L);
    }

    @Test
    void testDeleteNotFound() {
        Long id = 1L;
        when(repo.existsByIdAndFirmId(id, 1L)).thenReturn(false);

        boolean result = service.delete(id);

        assertFalse(result);
    }
}
