package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FirmDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirmDetailsService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testList() throws Exception {
        when(service.list()).thenReturn(Arrays.asList(new FirmDetails()));
        mockMvc.perform(get("/api/firm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testCreate() throws Exception {
        FirmDetails f = new FirmDetails();
        f.setId(1L);
        when(service.create()).thenReturn(f);
        mockMvc.perform(post("/api/firm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void testCreateWithBody() throws Exception {
        FirmDetails f = new FirmDetails();
        f.setId(2L);
        f.setFirmName("Sai Enterprises");
        when(service.create(any(FirmDetails.class))).thenReturn(f);
        mockMvc.perform(post("/api/firm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.firmName").value("Sai Enterprises"));
    }

    @Test
    void testUpdate() throws Exception {
        FirmDetails f = new FirmDetails();
        f.setFirmName("Updated");
        when(service.update(eq(1L), any(FirmDetails.class))).thenReturn(f);
        mockMvc.perform(put("/api/firm/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("Updated"));
    }
}
