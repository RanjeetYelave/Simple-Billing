package com.billing.simple.billsoft.licensing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnoozeState {

    private Instant licenseSnoozedUntil;
    private int licenseSnoozedRevision;

    private Instant dpSnoozedUntil;
    private int dpSnoozedRevision;
    private boolean dpPermanentlySnoozed;

    public SnoozeState() {
    }

    public Instant getLicenseSnoozedUntil() {
        return licenseSnoozedUntil;
    }

    public void setLicenseSnoozedUntil(Instant licenseSnoozedUntil) {
        this.licenseSnoozedUntil = licenseSnoozedUntil;
    }

    public int getLicenseSnoozedRevision() {
        return licenseSnoozedRevision;
    }

    public void setLicenseSnoozedRevision(int licenseSnoozedRevision) {
        this.licenseSnoozedRevision = licenseSnoozedRevision;
    }

    public Instant getDpSnoozedUntil() {
        return dpSnoozedUntil;
    }

    public void setDpSnoozedUntil(Instant dpSnoozedUntil) {
        this.dpSnoozedUntil = dpSnoozedUntil;
    }

    public int getDpSnoozedRevision() {
        return dpSnoozedRevision;
    }

    public void setDpSnoozedRevision(int dpSnoozedRevision) {
        this.dpSnoozedRevision = dpSnoozedRevision;
    }

    public boolean isDpPermanentlySnoozed() {
        return dpPermanentlySnoozed;
    }

    public void setDpPermanentlySnoozed(boolean dpPermanentlySnoozed) {
        this.dpPermanentlySnoozed = dpPermanentlySnoozed;
    }
}
