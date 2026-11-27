package com.billing.simple.billsoft.telegram.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_user_sessions")
public class TelegramUserSession {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "active_firm_id")
    private Long activeFirmId;

    @Column(name = "conversation_state", length = 50, nullable = false)
    private String conversationState = "IDLE";

    @Column(name = "active_draft_id")
    private Long activeDraftId;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    public TelegramUserSession() {}

    public TelegramUserSession(Long chatId, Long userId) {
        this.chatId = chatId;
        this.userId = userId;
        this.conversationState = "IDLE";
        this.lastActivityAt = LocalDateTime.now();
    }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getActiveFirmId() { return activeFirmId; }
    public void setActiveFirmId(Long activeFirmId) { this.activeFirmId = activeFirmId; }

    public String getConversationState() { return conversationState; }
    public void setConversationState(String conversationState) { this.conversationState = conversationState; }

    public Long getActiveDraftId() { return activeDraftId; }
    public void setActiveDraftId(Long activeDraftId) { this.activeDraftId = activeDraftId; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
}
