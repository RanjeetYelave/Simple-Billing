package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class InboxMessageService {
    private final InboxMessageRepository repository;

    public InboxMessageService(InboxMessageRepository repository) {
        this.repository = repository;
    }

    public List<InboxMessage> getMessagesByFirm(Long firmId) {
        return repository.findByFirmIdOrderByCreatedAtDesc(firmId);
    }

    public InboxMessage createMessage(InboxMessage msg) {
        return repository.save(msg);
    }

    @Transactional
    public InboxMessage markAsRead(Long id) {
        InboxMessage msg = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Message not found"));
        msg.setRead(true);
        return msg;
    }
}
