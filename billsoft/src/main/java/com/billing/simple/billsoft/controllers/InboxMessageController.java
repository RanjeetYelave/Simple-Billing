package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.service.InboxMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class InboxMessageController {
    private final InboxMessageService service;

    public InboxMessageController(InboxMessageService service) {
        this.service = service;
    }

    @GetMapping
    public List<InboxMessage> listByFirm(@RequestParam Long firmId) {
        return service.getMessagesByFirm(firmId);
    }

    @PostMapping
    public InboxMessage create(@RequestBody InboxMessage msg) {
        return service.createMessage(msg);
    }

    @PutMapping("/{id}/read")
    public InboxMessage markRead(@PathVariable Long id) {
        return service.markAsRead(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = service.deleteMessage(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
