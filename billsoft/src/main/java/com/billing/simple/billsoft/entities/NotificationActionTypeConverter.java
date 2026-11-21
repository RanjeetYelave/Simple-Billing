package com.billing.simple.billsoft.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationActionTypeConverter implements AttributeConverter<NotificationActionType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationActionType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public NotificationActionType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        return NotificationActionType.from(dbData);
    }
}
