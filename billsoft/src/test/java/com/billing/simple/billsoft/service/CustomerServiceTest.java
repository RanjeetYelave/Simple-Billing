package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.repo.CustomerRepository;
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
    }

    @Test
    void testCreate() {
        Customer customer = new Customer();
        customer.setName("John Doe");
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
        when(repo.findById(id)).thenReturn(Optional.of(customer));

        Customer result = service.getById(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testUpdate() {
        Long id = 1L;
        Customer existing = new Customer();
        existing.setId(id);
        existing.setName("Old Name");

        Customer updated = new Customer();
        updated.setName("New Name");

        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Customer result = service.update(id, updated);

        assertNotNull(result);
        assertEquals("New Name", result.getName());
    }

    @Test
    void testUpdateNotFound() {
        Long id = 1L;
        when(repo.findById(id)).thenReturn(Optional.empty());

        Customer result = service.update(id, new Customer());

        assertNull(result);
    }

    @Test
    void testDelete() {
        Long id = 1L;
        when(repo.existsById(id)).thenReturn(true);

        boolean result = service.delete(id);

        assertTrue(result);
        verify(repo, times(1)).deleteById(id);
    }

    @Test
    void testDeleteNotFound() {
        Long id = 1L;
        when(repo.existsById(id)).thenReturn(false);

        boolean result = service.delete(id);

        assertFalse(result);
    }
}
