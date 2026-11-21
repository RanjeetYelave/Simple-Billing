package com.billing.simple.billsoft.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationStatusConverter implements AttributeConverter<NotificationStatus, String> {

    @Override
    public String convertToDatabaseColumn(NotificationStatus attribute) {
        if (attribute == null) {
            return NotificationStatus.UNREAD.name();
        }
        return attribute.name();
    }

    @Override
    public NotificationStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return NotificationStatus.UNREAD;
        }
        return NotificationStatus.from(dbData);
    }
}
