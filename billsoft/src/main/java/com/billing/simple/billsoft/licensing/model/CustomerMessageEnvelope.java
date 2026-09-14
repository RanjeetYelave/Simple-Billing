package com.billing.simple.billsoft.licensing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Envelope file for messages published to messages/<machine-id>.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerMessageEnvelope {

    private String machineId;
    private List<CustomerMessage> messages = new ArrayList<>();

    public CustomerMessageEnvelope() {
    }

    public CustomerMessageEnvelope(String machineId, List<CustomerMessage> messages) {
        this.machineId = machineId;
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public List<CustomerMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<CustomerMessage> messages) {
        this.messages = messages;
    }
}
