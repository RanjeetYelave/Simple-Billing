package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Note;
import com.billing.simple.billsoft.repo.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NoteService {
    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public List<Note> getAll() {
        return noteRepository.findAll();
    }

    public List<Note> getByFirm(Long firmId) {
        return noteRepository.findByFirmId(firmId);
    }

    public Note create(Note note) {
        return noteRepository.save(note);
    }

    @Transactional
    public Note update(Long id, Note updated) {
        Note n = noteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Note not found"));
        n.setTitle(updated.getTitle());
        n.setContent(updated.getContent());
        n.setTags(updated.getTags());
        n.setCustomerId(updated.getCustomerId());
        n.setFirmId(updated.getFirmId());
        return noteRepository.save(n);
    }

    @Transactional
    public boolean delete(Long id) {
        if (!noteRepository.existsById(id)) return false;
        noteRepository.deleteById(id);
        return true;
    }
}
