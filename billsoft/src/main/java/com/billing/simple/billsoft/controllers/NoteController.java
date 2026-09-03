package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Note;
import com.billing.simple.billsoft.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<Note> listAll(@RequestParam(value = "firmId", required = false) Long firmId,
                              @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        Long fid = firmId != null ? firmId : firmIdHeader;
        if (fid != null) {
            return noteService.getByFirm(fid);
        }
        return java.util.Collections.emptyList();
    }

    @GetMapping("/firm/{firmId}")
    public List<Note> listByFirm(@PathVariable Long firmId) {
        return noteService.getByFirm(firmId);
    }

    @PostMapping
    public Note create(@RequestBody Note note) {
        return noteService.create(note);
    }

    @PutMapping("/{id}")
    public Note update(@PathVariable Long id, @RequestBody Note note) {
        return noteService.update(id, note);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = noteService.delete(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
