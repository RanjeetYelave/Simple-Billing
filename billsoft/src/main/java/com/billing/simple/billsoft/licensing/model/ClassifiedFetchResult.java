package com.billing.simple.billsoft.licensing.model;

/**
 * Immutable per-request classification and payload result for remote licensing operations.
 */
public final class ClassifiedFetchResult {

    private final FetchClassification classification;
    private final int httpStatusCode;
    private final String body;
    private final long elapsedMillis;
    private final String etag;

    public ClassifiedFetchResult(FetchClassification classification, int httpStatusCode, String body, long elapsedMillis) {
        this(classification, httpStatusCode, body, elapsedMillis, null);
    }

    public ClassifiedFetchResult(FetchClassification classification, int httpStatusCode, String body, long elapsedMillis, String etag) {
        this.classification = classification;
        this.httpStatusCode = httpStatusCode;
        this.body = body;
        this.elapsedMillis = elapsedMillis;
        this.etag = etag;
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

    public String getEtag() {
        return etag;
    }

    public boolean isSuccess() {
        return classification == FetchClassification.SUCCESS || classification == FetchClassification.NOT_MODIFIED
                || httpStatusCode == 200 || httpStatusCode == 304;
    }

    public boolean isNotModified() {
        return classification == FetchClassification.NOT_MODIFIED || httpStatusCode == 304;
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
                ", etag=" + (etag != null ? etag : "none") +
                '}';
    }
}
