package com.billing.simple.billsoft.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

@JsonComponent
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart()
            .appendPattern("['T'][' '][HH:mm[:ss[.SSS]]]")
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 9)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();

        // 1. Try standard ISO / space separated LocalDateTime
        try {
            if (text.contains("T")) {
                if (text.length() == 16) { // "yyyy-MM-dd'T'HH:mm"
                    return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                }
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (text.contains(" ")) {
                if (text.length() == 16) { // "yyyy-MM-dd HH:mm"
                    return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                }
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception ignored) {
        }

        // 2. Try Date only formats with default 09:00:00 AM time
        String[] datePatterns = new String[]{
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "yyyy/MM/dd",
                "d/M/yyyy",
                "d-M-yyyy"
        };

        for (String pattern : datePatterns) {
            try {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern));
                return date.atTime(LocalTime.of(9, 0));
            } catch (Exception ignored) {
            }
        }

        try {
            return LocalDateTime.parse(text, FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
