package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Note;
import com.billing.simple.billsoft.repo.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NoteService {
    private final NoteRepository noteRepository;

    private final com.billing.simple.billsoft.repo.CustomerRepository customerRepository;

    public NoteService(NoteRepository noteRepository, com.billing.simple.billsoft.repo.CustomerRepository customerRepository) {
        this.noteRepository = noteRepository;
        this.customerRepository = customerRepository;
    }

    public List<Note> getAll() {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            return noteRepository.findByFirmId(firmId);
        }
        return noteRepository.findAll();
    }

    public List<Note> getByFirm(Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        return noteRepository.findByFirmId(authoritativeFirmId != null ? authoritativeFirmId : firmId);
    }

    public static int countCodePoints(String s) {
        if (s == null) return 0;
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
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            note.setFirmId(firmId);
        }
        if (note.getCustomerId() != null && note.getFirmId() != null) {
            if (!customerRepository.existsByIdAndFirmId(note.getCustomerId(), note.getFirmId())) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException("Referenced customer does not belong to the current firm");
            }
        }
        return noteRepository.save(note);
    }

    @Transactional
    public Note update(Long id, Note updated) {
        validateNote(updated);
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Note n = (firmId != null)
                ? noteRepository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Note not found"))
                : noteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Note not found"));

        if (updated.getCustomerId() != null && n.getFirmId() != null) {
            if (!customerRepository.existsByIdAndFirmId(updated.getCustomerId(), n.getFirmId())) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException("Referenced customer does not belong to the current firm");
            }
            n.setCustomerId(updated.getCustomerId());
        } else if (updated.getCustomerId() == null) {
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
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (!noteRepository.existsByIdAndFirmId(id, firmId)) return false;
            noteRepository.deleteByIdAndFirmId(id, firmId);
            return true;
        }
        if (!noteRepository.existsById(id)) return false;
        noteRepository.deleteById(id);
        return true;
    }
}
