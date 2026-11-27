package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import com.billing.simple.billsoft.telegram.repo.TelegramUserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TelegramSessionManager {

    private final TelegramUserSessionRepository sessionRepo;

    public TelegramSessionManager(TelegramUserSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Transactional
    public TelegramUserSession getOrCreateSession(Long chatId, Long userId) {
        Optional<TelegramUserSession> opt = sessionRepo.findByChatId(chatId);
        if (opt.isPresent()) {
            TelegramUserSession session = opt.get();
            session.setUserId(userId);
            session.setLastActivityAt(LocalDateTime.now());
            return sessionRepo.save(session);
        } else {
            TelegramUserSession session = new TelegramUserSession(chatId, userId);
            return sessionRepo.save(session);
        }
    }

    public Optional<TelegramUserSession> getSession(Long chatId) {
        return sessionRepo.findByChatId(chatId);
    }

    @Transactional
    public void setChatFirm(Long chatId, Long firmId) {
        sessionRepo.findByChatId(chatId).ifPresent(s -> {
            s.setActiveFirmId(firmId);
            s.setLastActivityAt(LocalDateTime.now());
            sessionRepo.save(s);
        });
    }

    @Transactional
    public void setConversationState(Long chatId, String state) {
        sessionRepo.findByChatId(chatId).ifPresent(s -> {
            s.setConversationState(state);
            s.setLastActivityAt(LocalDateTime.now());
            sessionRepo.save(s);
        });
    }

    @Transactional
    public void setActiveDraftId(Long chatId, Long draftId) {
        sessionRepo.findByChatId(chatId).ifPresent(s -> {
            s.setActiveDraftId(draftId);
            s.setLastActivityAt(LocalDateTime.now());
            sessionRepo.save(s);
        });
    }

    @Transactional
    public void clearSessionState(Long chatId) {
        sessionRepo.findByChatId(chatId).ifPresent(s -> {
            s.setConversationState("IDLE");
            s.setActiveDraftId(null);
            s.setLastActivityAt(LocalDateTime.now());
            sessionRepo.save(s);
        });
    }
}
