package com.billing.simple.billsoft.telegram.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_authorized_users")
public class TelegramAuthorizedUser {

    @Id
    @Column(name = "user_id")
    private Long userId; // 64-bit Telegram user ID

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE"; // ACTIVE, REVOKED

    @Column(name = "authorized_by", length = 50, nullable = false)
    private String authorizedBy = "DESKTOP_OWNER";

    @Column(name = "authorized_at", nullable = false)
    private LocalDateTime authorizedAt = LocalDateTime.now();

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    public TelegramAuthorizedUser() {}

    public TelegramAuthorizedUser(Long userId, String displayName, String username, String authorizedBy) {
        this.userId = userId;
        this.displayName = displayName;
        this.username = username;
        this.authorizedBy = authorizedBy;
        this.status = "ACTIVE";
        this.authorizedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAuthorizedBy() { return authorizedBy; }
    public void setAuthorizedBy(String authorizedBy) { this.authorizedBy = authorizedBy; }

    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(LocalDateTime authorizedAt) { this.authorizedAt = authorizedAt; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
