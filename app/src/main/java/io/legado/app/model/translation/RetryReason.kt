package io.legado.app.model.translation

/**
 * Reasons for retrying a translation request.
 * Used to analyze failures and decide appropriate retry strategies.
 */
enum class RetryReason {
    /** Network connectivity issues */
    NETWORK_ERROR,

    /** API returned rate limit error (429) */
    RATE_LIMIT,

    /** API returned server error (5xx) */
    SERVER_ERROR,

    /** API returned authentication/permission error (401, 403) */
    AUTH_ERROR,

    /** Request timeout */
    TIMEOUT,

    /** API returned empty response */
    EMPTY_RESPONSE,

    /** Malformed response that couldn't be parsed */
    PARSE_ERROR,

    /** Unknown error that might be transient */
    UNKNOWN,

    /** No retry needed, permanent failure */
    PERMANENT_FAILURE
}