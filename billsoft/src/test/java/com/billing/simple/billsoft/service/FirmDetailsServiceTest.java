package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirmDetailsServiceTest {

    @Mock
    private FirmDetailsRepository repo;

    @InjectMocks
    private FirmDetailsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testList() {
        List<FirmDetails> firms = Arrays.asList(new FirmDetails(), new FirmDetails());
        when(repo.findAll()).thenReturn(firms);

        List<FirmDetails> result = service.list();

        assertEquals(2, result.size());
    }

    @Test
    void testGet() {
        Long id = 1L;
        FirmDetails firm = new FirmDetails();
        firm.setId(id);
        when(repo.findById(id)).thenReturn(Optional.of(firm));

        FirmDetails result = service.get(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testGetFirstWithData() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Existing Firm");
        when(repo.findAll()).thenReturn(Arrays.asList(firm));

        FirmDetails result = service.getFirst();

        assertEquals("Existing Firm", result.getFirmName());
    }

    @Test
    void testGetFirstEmpty() {
        when(repo.findAll()).thenReturn(new ArrayList<>());

        FirmDetails result = service.getFirst();

        assertNull(result);
    }

    @Test
    void testCreate() {
        when(repo.save(any(FirmDetails.class))).thenAnswer(i -> i.getArguments()[0]);

        FirmDetails result = service.create();

        assertNotNull(result);
        assertEquals("New Firm", result.getFirmName());
    }

    @Test
    void testUpdate() {
        Long id = 1L;
        FirmDetails existing = new FirmDetails();
        existing.setId(id);
        existing.setFirmName("Old Firm");

        FirmDetails payload = new FirmDetails();
        payload.setFirmName(" Updated Firm ");
        payload.setPhone(" 1234567890 ");
        payload.setUpiId(" store@okaxis ");

        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(FirmDetails.class))).thenAnswer(i -> i.getArguments()[0]);

        FirmDetails result = service.update(id, payload);

        assertNotNull(result);
        assertEquals("Updated Firm", result.getFirmName());
        assertEquals("1234567890", result.getPhone());
        assertEquals("store@okaxis", result.getUpiId());
    }

    @Test
    void testUpdateNotFound() {
        Long id = 1L;
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.update(id, new FirmDetails()));
    }

    @Test
    void testDelete() {
        Long id = 1L;
        service.delete(id);
        verify(repo, times(1)).deleteById(id);
    }
}
