package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.BusinessLetter;
import com.billing.simple.billsoft.entities.LetterRecipientType;
import com.billing.simple.billsoft.entities.LetterStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BusinessLetterService {

    BusinessLetter createLetter(BusinessLetter letter);

    BusinessLetter updateLetter(Long id, BusinessLetter updated);

    List<BusinessLetter> getLettersByFirm(Long firmId, LetterRecipientType recipientType, Long partyId, Long customerId, LetterStatus status, LocalDate start, LocalDate end);

    Optional<BusinessLetter> getLetterById(Long id, Long firmId);

    BusinessLetter updateStatus(Long id, Long firmId, LetterStatus status);

    void deleteLetter(Long id, Long firmId);

    String generateNextLetterNumber(Long firmId);

    byte[] generateLetterPdf(Long id, Long firmId) throws Exception;
}
