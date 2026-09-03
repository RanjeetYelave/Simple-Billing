package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("regression")
@Tag("integration")
@DisplayName("Business Letters, Reminders, Notes, Expenses & Inbox REST Controller Regression Tests")
class BusinessLetterAndMiscControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessLetterService letterService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private ObjectMapper objectMapper;

    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Global Corporate Hub");
        firm.setGstin("27AAACG9988D1Z2");
        firm.setAddressLine1("Corporate Tower 4");
        firmService.create(firm);
    }

    @Test
    @DisplayName("Should test Business Letter creation, number generation, and PDF export endpoints")
    void testBusinessLetters() throws Exception {
        // 1. Next letter number
        mockMvc.perform(get("/api/letters/next-number?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextNumber").exists());

        // 2. Create Letter
        BusinessLetter letter = BusinessLetter.builder()
                .subject("Formal Quotation Agreement")
                .content("We hereby confirm the supply of components.")
                .recipientType(LetterRecipientType.CUSTOM)
                .recipientName("Starline Tech")
                .letterDate(LocalDate.now())
                .firmId(testFirmId)
                .status(LetterStatus.DRAFT)
                .build();

        BusinessLetter created = letterService.createLetter(letter);

        // 3. List letters
        mockMvc.perform(get("/api/letters?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subject").value("Formal Quotation Agreement"));

        // 4. Download Letter PDF
        mockMvc.perform(get("/api/letters/" + created.getId() + "/pdf?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @DisplayName("Should test Expenses, Reminders, Notes and Inbox Controller endpoints")
    void testMiscControllers() throws Exception {
        // 1. Expense Controller
        Map<String, Object> expReq = Map.of(
                "title", "Office Rent",
                "amount", 25000.0,
                "category", "Rent",
                "expenseDate", LocalDate.now().toString(),
                "firmId", testFirmId
        );
        mockMvc.perform(post("/api/expenses?firmId=" + testFirmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Office Rent"));

        mockMvc.perform(get("/api/expenses?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/expenses/summary?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllTime").exists());

        // 2. Reminder Controller
        Map<String, Object> reminderReq = Map.of(
                "title", "Tax Audit Review",
                "dueDate", LocalDateTime.now().plusDays(3).toString(),
                "firmId", testFirmId
        );
        mockMvc.perform(post("/api/reminders?firmId=" + testFirmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reminderReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tax Audit Review"));

        mockMvc.perform(get("/api/reminders?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 3. Note Controller
        Map<String, Object> noteReq = Map.of(
                "title", "Monthly KPI Targets",
                "content", "Target 100 new client conversions.",
                "firmId", testFirmId
        );
        mockMvc.perform(post("/api/notes?firmId=" + testFirmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Monthly KPI Targets"));

        mockMvc.perform(get("/api/notes?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Inbox Messages Controller
        mockMvc.perform(get("/api/messages?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
