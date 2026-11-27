package com.billing.simple.billsoft.telegram.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_link_challenges")
public class TelegramLinkChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_hash", length = 128, nullable = false, unique = true)
    private String challengeHash;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING"; // PENDING, USED, EXPIRED

    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "used_by_user_id")
    private Long usedByUserId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public TelegramLinkChallenge() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChallengeHash() { return challengeHash; }
    public void setChallengeHash(String challengeHash) { this.challengeHash = challengeHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }

    public Long getUsedByUserId() { return usedByUserId; }
    public void setUsedByUserId(Long usedByUserId) { this.usedByUserId = usedByUserId; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
