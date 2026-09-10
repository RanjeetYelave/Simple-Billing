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

    public Note create(Note note) {
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
