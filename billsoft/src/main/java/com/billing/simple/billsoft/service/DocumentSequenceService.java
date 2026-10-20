package com.billing.simple.billsoft.service;

import java.time.LocalDate;

/**
 * Authoritative document sequence service for allocating atomic, monotonic, firm-isolated,
 * and fiscal-year-scoped document numbers for Invoices, Estimates, Sales Returns, and Purchase Orders.
 */
public interface DocumentSequenceService {

    /**
     * Atomically generates and increments the next document number for the specified type, firm, and date.
     */
    String generateNextNumber(DocumentType type, Long firmId, LocalDate date);

    default String generateNextNumber(DocumentType type, Long firmId) {
        return generateNextNumber(type, firmId, LocalDate.now());
    }

    /**
     * Peeks at the next expected document number without incrementing the sequence counter.
     */
    String peekNextNumber(DocumentType type, Long firmId, LocalDate date);

    default String peekNextNumber(DocumentType type, Long firmId) {
        return peekNextNumber(type, firmId, LocalDate.now());
    }

    /**
     * Synchronizes sequence counters if a document with an explicit or imported number has a higher sequence.
     */
    void syncSequenceIfApplicable(DocumentType type, Long firmId, String number, LocalDate date);

    default void syncSequenceIfApplicable(DocumentType type, Long firmId, String number) {
        syncSequenceIfApplicable(type, firmId, number, LocalDate.now());
    }
}
