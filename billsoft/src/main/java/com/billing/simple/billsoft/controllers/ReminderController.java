package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Reminder;
import com.billing.simple.billsoft.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public List<Reminder> listAll() {
        return reminderService.getAll();
    }

    @GetMapping("/firm/{firmId}")
    public List<Reminder> listByFirm(@PathVariable Long firmId) {
        return reminderService.getByFirm(firmId);
    }

    @GetMapping("/firm/{firmId}/active")
    public List<Reminder> listActiveByFirm(@PathVariable Long firmId) {
        return reminderService.getActiveByFirm(firmId);
    }

    @PostMapping
    public Reminder create(@RequestBody Reminder reminder) {
        return reminderService.create(reminder);
    }

    @PutMapping("/{id}")
    public Reminder update(@PathVariable Long id, @RequestBody Reminder reminder) {
        return reminderService.update(id, reminder);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = reminderService.delete(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @RequestMapping(value = "/{id}/done", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public Reminder markDone(@PathVariable Long id) {
        return reminderService.markDone(id);
    }
}
