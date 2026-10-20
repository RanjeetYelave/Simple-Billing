package com.billing.simple.billsoft.licensing.model;

/**
 * Categorizes the outcome of a remote license registry fetch attempt.
 */
public enum FetchClassification {
    SUCCESS,
    RESOURCE_NOT_FOUND,         // HTTP 404
    FORBIDDEN_OR_UNAUTHORIZED,  // HTTP 403 / 401
    SERVER_ERROR,               // HTTP 500, 502, 503, 504
    CONNECT_TIMEOUT,            // Connection could not be established in time
    READ_TIMEOUT,               // Server accepted connection but stalled response
    CONNECTION_FAILURE,         // DNS failure, Connection refused, or unreachable host
    MALFORMED_RESPONSE,         // Response body could not be parsed or verified
    OVERALL_DEADLINE_EXCEEDED   // Overall 3,500ms budget expired before operation could finish
}
