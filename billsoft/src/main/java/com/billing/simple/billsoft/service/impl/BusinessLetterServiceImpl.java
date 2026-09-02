package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.entities.BusinessLetter;
import com.billing.simple.billsoft.entities.LetterRecipientType;
import com.billing.simple.billsoft.entities.LetterStatus;
import com.billing.simple.billsoft.repositories.BusinessLetterRepository;
import com.billing.simple.billsoft.service.BusinessLetterPdfService;
import com.billing.simple.billsoft.service.BusinessLetterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class BusinessLetterServiceImpl implements BusinessLetterService {

    private final BusinessLetterRepository letterRepository;
    private final BusinessLetterPdfService pdfService;

    public BusinessLetterServiceImpl(BusinessLetterRepository letterRepository,
                                    BusinessLetterPdfService pdfService) {
        this.letterRepository = letterRepository;
        this.pdfService = pdfService;
    }

    @Override
    public BusinessLetter createLetter(BusinessLetter letter) {
        if (letter.getFirmId() == null) {
            throw new IllegalArgumentException("Firm ID is required");
        }
        if (letter.getRecipientName() == null || letter.getRecipientName().trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient name is required");
        }
        if (letter.getSubject() == null || letter.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Letter subject is required");
        }
        if (letter.getContent() == null || letter.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Letter content is required");
        }

        if (letter.getLetterNumber() == null || letter.getLetterNumber().trim().isEmpty()) {
            letter.setLetterNumber(generateNextLetterNumber(letter.getFirmId()));
        }

        return letterRepository.save(letter);
    }

    @Override
    public BusinessLetter updateLetter(Long id, BusinessLetter updated) {
        BusinessLetter existing = (updated.getFirmId() != null
                ? letterRepository.findByIdAndFirmId(id, updated.getFirmId())
                : letterRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Letter not found with id: " + id));

        if (updated.getLetterNumber() != null && !updated.getLetterNumber().trim().isEmpty()) {
            existing.setLetterNumber(updated.getLetterNumber().trim());
        }
        if (updated.getLetterDate() != null) {
            existing.setLetterDate(updated.getLetterDate());
        }

        // FROM Sender fields
        if (updated.getSenderType() != null) {
            existing.setSenderType(updated.getSenderType());
        }
        existing.setSenderName(updated.getSenderName());
        existing.setSenderCompany(updated.getSenderCompany());
        existing.setSenderAddress(updated.getSenderAddress());
        existing.setSenderPhone(updated.getSenderPhone());
        existing.setSenderEmail(updated.getSenderEmail());
        existing.setSenderGstin(updated.getSenderGstin());

        // TO Recipient fields
        if (updated.getRecipientType() != null) {
            existing.setRecipientType(updated.getRecipientType());
        }
        existing.setPartyId(updated.getPartyId());
        existing.setCustomerId(updated.getCustomerId());
        existing.setRecipientName(updated.getRecipientName());
        existing.setRecipientDesignation(updated.getRecipientDesignation());
        existing.setRecipientCompany(updated.getRecipientCompany());
        existing.setRecipientAddress(updated.getRecipientAddress());
        existing.setRecipientPhone(updated.getRecipientPhone());
        existing.setRecipientEmail(updated.getRecipientEmail());

        // Content & Signatory
        existing.setSubject(updated.getSubject());
        existing.setCategory(updated.getCategory());
        existing.setContent(updated.getContent());
        existing.setSignatoryName(updated.getSignatoryName());
        existing.setSignatoryDesignation(updated.getSignatoryDesignation());
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        existing.setIncludeHeader(updated.getIncludeHeader());
        existing.setIncludeFooter(updated.getIncludeFooter());

        return letterRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessLetter> getLettersByFirm(Long firmId, LetterRecipientType recipientType, Long partyId, Long customerId, LetterStatus status, LocalDate start, LocalDate end) {
        List<BusinessLetter> list = letterRepository.findByFirmIdOrderByLetterDateDescIdDesc(firmId);

        return list.stream()
                .filter(l -> recipientType == null || l.getRecipientType() == recipientType)
                .filter(l -> partyId == null || (l.getPartyId() != null && l.getPartyId().equals(partyId)))
                .filter(l -> customerId == null || (l.getCustomerId() != null && l.getCustomerId().equals(customerId)))
                .filter(l -> status == null || l.getStatus() == status)
                .filter(l -> start == null || !l.getLetterDate().isBefore(start))
                .filter(l -> end == null || !l.getLetterDate().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessLetter> getLetterById(Long id, Long firmId) {
        if (firmId != null) {
            return letterRepository.findByIdAndFirmId(id, firmId);
        }
        return letterRepository.findById(id);
    }

    @Override
    public BusinessLetter updateStatus(Long id, Long firmId, LetterStatus status) {
        BusinessLetter letter = (firmId != null
                ? letterRepository.findByIdAndFirmId(id, firmId)
                : letterRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Letter not found with id: " + id));
        letter.setStatus(status);
        return letterRepository.save(letter);
    }

    @Override
    public void deleteLetter(Long id, Long firmId) {
        BusinessLetter letter = (firmId != null
                ? letterRepository.findByIdAndFirmId(id, firmId)
                : letterRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Letter not found with id: " + id));
        letterRepository.delete(letter);
    }

    @Override
    @Transactional(readOnly = true)
    public String generateNextLetterNumber(Long firmId) {
        long count = letterRepository.countByFirmId(firmId);
        int currentYear = LocalDate.now().getYear();
        return String.format("LTR-%d-%04d", currentYear, count + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateLetterPdf(Long id, Long firmId) throws Exception {
        BusinessLetter letter = (firmId != null
                ? letterRepository.findByIdAndFirmId(id, firmId)
                : letterRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Letter not found with id: " + id));
        return pdfService.generatePdf(letter);
    }
}
