package com.billing.simple.billsoft.licensing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Single signed customer notification/reminder message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerMessage {

    private String messageId;
    private String title;
    private String body;
    private Instant createdAt;
    private String signature;
    private boolean read;

    public CustomerMessage() {
    }

    public CustomerMessage(String messageId, String title, String body, Instant createdAt, String signature) {
        this.messageId = messageId;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
        this.signature = signature;
        this.read = false;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
