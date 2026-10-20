package com.billing.simple.billsoft.licensing.model;

/**
 * Immutable per-request classification and payload result for remote licensing operations.
 */
public final class ClassifiedFetchResult {

    private final FetchClassification classification;
    private final int httpStatusCode;
    private final String body;
    private final long elapsedMillis;

    public ClassifiedFetchResult(FetchClassification classification, int httpStatusCode, String body, long elapsedMillis) {
        this.classification = classification;
        this.httpStatusCode = httpStatusCode;
        this.body = body;
        this.elapsedMillis = elapsedMillis;
    }

    public FetchClassification getClassification() {
        return classification;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getBody() {
        return body;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public boolean isSuccess() {
        return classification == FetchClassification.SUCCESS;
    }

    public boolean isTerminalNetworkFailure() {
        return classification == FetchClassification.CONNECT_TIMEOUT
                || classification == FetchClassification.READ_TIMEOUT
                || classification == FetchClassification.CONNECTION_FAILURE
                || classification == FetchClassification.OVERALL_DEADLINE_EXCEEDED;
    }

    @Override
    public String toString() {
        return "ClassifiedFetchResult{" +
                "classification=" + classification +
                ", httpStatusCode=" + httpStatusCode +
                ", elapsedMillis=" + elapsedMillis +
                '}';
    }
}
