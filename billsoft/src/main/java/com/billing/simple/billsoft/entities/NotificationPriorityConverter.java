package com.billing.simple.billsoft.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationPriorityConverter implements AttributeConverter<NotificationPriority, String> {

    @Override
    public String convertToDatabaseColumn(NotificationPriority attribute) {
        if (attribute == null) {
            return NotificationPriority.NORMAL.name();
        }
        return attribute.name();
    }

    @Override
    public NotificationPriority convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return NotificationPriority.NORMAL;
        }
        return NotificationPriority.from(dbData);
    }
}
