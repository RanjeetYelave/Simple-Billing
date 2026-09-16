package com.billing.simple.billsoft.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlexibleLocalDateTimeDeserializerTest {

    private ObjectMapper objectMapper;

    static class TestDto {
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        private LocalDateTime dueDate;

        public LocalDateTime getDueDate() {
            return dueDate;
        }

        public void setDueDate(LocalDateTime dueDate) {
            this.dueDate = dueDate;
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testParseDateOnlyString() throws Exception {
        String json = "{\"dueDate\":\"2026-09-27\"}";
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        assertNotNull(dto.getDueDate());
        assertEquals(LocalDateTime.of(2026, 9, 27, 9, 0, 0), dto.getDueDate());
    }

    @Test
    void testParseIsoDateTimeString() throws Exception {
        String json = "{\"dueDate\":\"2026-09-27T09:00:00\"}";
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        assertNotNull(dto.getDueDate());
        assertEquals(LocalDateTime.of(2026, 9, 27, 9, 0, 0), dto.getDueDate());
    }

    @Test
    void testParseSpaceSeparatedDateTimeString() throws Exception {
        String json = "{\"dueDate\":\"2026-09-27 15:30:00\"}";
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        assertNotNull(dto.getDueDate());
        assertEquals(LocalDateTime.of(2026, 9, 27, 15, 30, 0), dto.getDueDate());
    }

    @Test
    void testParseSlashDateFormat() throws Exception {
        String json = "{\"dueDate\":\"27/09/2026\"}";
        TestDto dto = objectMapper.readValue(json, TestDto.class);
        assertNotNull(dto.getDueDate());
        assertEquals(LocalDateTime.of(2026, 9, 27, 9, 0, 0), dto.getDueDate());
    }
}
