package com.billing.simple.billsoft.entities;

/**
 * Type of recipient for an official business letter.
 */
public enum LetterRecipientType {
    PARTY,      // Vendor / Supplier
    CUSTOMER,   // Client / Buyer
    CUSTOM      // External individual or organization
}
