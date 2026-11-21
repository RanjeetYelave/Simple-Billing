package com.billing.simple.billsoft.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationCategoryConverter implements AttributeConverter<NotificationCategory, String> {

    @Override
    public String convertToDatabaseColumn(NotificationCategory attribute) {
        if (attribute == null) {
            return NotificationCategory.SYSTEM.name();
        }
        return attribute.name();
    }

    @Override
    public NotificationCategory convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return NotificationCategory.SYSTEM;
        }
        return NotificationCategory.from(dbData);
    }
}
