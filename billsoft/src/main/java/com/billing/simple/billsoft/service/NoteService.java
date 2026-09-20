package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Note;
import com.billing.simple.billsoft.repo.NoteRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NoteService {
    private final NoteRepository noteRepository;

    private final com.billing.simple.billsoft.repo.CustomerRepository customerRepository;

    public NoteService(NoteRepository noteRepository,
            com.billing.simple.billsoft.repo.CustomerRepository customerRepository) {
        this.noteRepository = noteRepository;
        this.customerRepository = customerRepository;
    }

    private Long resolveFirmId(Long explicitFirmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && explicitFirmId != null && !authoritativeFirmId.equals(explicitFirmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : explicitFirmId;
        if (target == null || target <= 0) {
            throw new TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    public List<Note> getAll() {
        Long firmId = TenantContext.getCurrentFirmId();
        return firmId != null ? noteRepository.findByFirmId(firmId) : noteRepository.findAll();
    }

    public List<Note> getByFirm(Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        return target != null ? noteRepository.findByFirmId(target) : noteRepository.findAll();
    }

    public static int countCodePoints(String s) {
        if (s == null)
            return 0;
        return s.codePointCount(0, s.length());
    }

    private void validateNote(Note note) {
        if (note == null) {
            throw new IllegalArgumentException("Note payload cannot be null");
        }
        if (note.getTitle() == null || note.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Note title is required.");
        }
        if (countCodePoints(note.getTitle()) > 200) {
            throw new IllegalArgumentException("Note title cannot exceed 200 characters.");
        }
        if (note.getContent() != null && countCodePoints(note.getContent()) > 50000) {
            throw new IllegalArgumentException("Note content cannot exceed 50,000 characters.");
        }
        if (note.getTags() != null && countCodePoints(note.getTags()) > 1000) {
            throw new IllegalArgumentException("Note tags cannot exceed 1,000 characters.");
        }
    }

    public Note create(Note note) {
        validateNote(note);
        Long firmId = note.getFirmId() != null ? note.getFirmId() : TenantContext.getCurrentFirmId();
        if (firmId != null) {
            note.setFirmId(firmId);
            if (note.getCustomerId() != null) {
                if (!customerRepository.existsByIdAndFirmId(note.getCustomerId(), firmId)) {
                    throw new TenantSecurityException(
                            "Referenced customer does not belong to the current firm");
                }
            }
        }
        return noteRepository.save(note);
    }

    @Transactional
    public Note update(Long id, Note updated) {
        validateNote(updated);
        Long firmId = TenantContext.getCurrentFirmId();
        Note n = (firmId != null ? noteRepository.findByIdAndFirmId(id, firmId) : noteRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));

        if (updated.getCustomerId() != null) {
            Long targetFid = n.getFirmId() != null ? n.getFirmId() : firmId;
            if (targetFid != null && !customerRepository.existsByIdAndFirmId(updated.getCustomerId(), targetFid)) {
                throw new TenantSecurityException(
                        "Referenced customer does not belong to the current firm");
            }
            n.setCustomerId(updated.getCustomerId());
        } else {
            n.setCustomerId(null);
        }

        n.setTitle(updated.getTitle());
        n.setContent(updated.getContent());
        n.setTags(updated.getTags());
        // Do NOT allow changing firmId
        return noteRepository.save(n);
    }

    @Transactional
    public boolean delete(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (!noteRepository.existsByIdAndFirmId(id, firmId))
                return false;
            noteRepository.deleteByIdAndFirmId(id, firmId);
            return true;
        } else {
            if (!noteRepository.existsById(id))
                return false;
            noteRepository.deleteById(id);
            return true;
        }
    }
}
