package com.billing.simple.billsoft.licensing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Immutable DTO representing a signed license payload.
 * Strictly backward compatible across Schema 1, 2, and 3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicensePayload {

    private Integer schemaVersion;
    private String licenseId;
    private String machineId;
    private String customerName;
    private String product;
    private String edition;
    private MembershipPlan plan;
    private LicenseStatus status;
    private int revision;
    private Instant issuedAt;
    private Instant expiresAt;
    private Boolean dataProtectionEnabled;
    private Instant dataProtectionExpiresAt;
    private String statusReason;
    private EmiPayload emi;
    private String signature;

    public LicensePayload() {
    }

    public LicensePayload(String licenseId, String machineId, String customerName, String product,
                          String edition, MembershipPlan plan, LicenseStatus status, int revision,
                          Instant issuedAt, Instant expiresAt, String statusReason, String signature) {
        this(licenseId, machineId, customerName, product, edition, plan, status, revision,
             issuedAt, expiresAt, null, null, statusReason, signature);
    }

    public LicensePayload(String licenseId, String machineId, String customerName, String product,
                          String edition, MembershipPlan plan, LicenseStatus status, int revision,
                          Instant issuedAt, Instant expiresAt, Boolean dataProtectionEnabled,
                          Instant dataProtectionExpiresAt, String statusReason, String signature) {
        this(3, licenseId, machineId, customerName, product, edition, plan, status, revision,
             issuedAt, expiresAt, dataProtectionEnabled, dataProtectionExpiresAt, statusReason, null, signature);
    }

    public LicensePayload(Integer schemaVersion, String licenseId, String machineId, String customerName, String product,
                          String edition, MembershipPlan plan, LicenseStatus status, int revision,
                          Instant issuedAt, Instant expiresAt, Boolean dataProtectionEnabled,
                          Instant dataProtectionExpiresAt, String statusReason, EmiPayload emi, String signature) {
        this.schemaVersion = schemaVersion;
        this.licenseId = licenseId;
        this.machineId = machineId;
        this.customerName = customerName;
        this.product = product;
        this.edition = edition;
        this.plan = plan;
        this.status = status;
        this.revision = revision;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.dataProtectionEnabled = dataProtectionEnabled;
        this.dataProtectionExpiresAt = dataProtectionExpiresAt;
        this.statusReason = statusReason;
        this.emi = emi;
        this.signature = signature;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(String licenseId) {
        this.licenseId = licenseId;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public MembershipPlan getPlan() {
        return plan;
    }

    public void setPlan(MembershipPlan plan) {
        this.plan = plan;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseStatus status) {
        this.status = status;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getDataProtectionEnabled() {
        return dataProtectionEnabled;
    }

    public void setDataProtectionEnabled(Boolean dataProtectionEnabled) {
        this.dataProtectionEnabled = dataProtectionEnabled;
    }

    public Instant getDataProtectionExpiresAt() {
        return dataProtectionExpiresAt;
    }

    public void setDataProtectionExpiresAt(Instant dataProtectionExpiresAt) {
        this.dataProtectionExpiresAt = dataProtectionExpiresAt;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public EmiPayload getEmi() {
        return emi;
    }

    public void setEmi(EmiPayload emi) {
        this.emi = emi;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
