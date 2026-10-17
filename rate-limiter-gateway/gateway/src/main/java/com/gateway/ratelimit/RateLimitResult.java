package com.gateway.ratelimit;

/**
 * Outcome of a rate-limit check.
 *
 * @param allowed        whether the request should proceed
 * @param remaining      remaining quota in the current window/bucket
 * @param limit          the configured limit for context
 * @param retryAfterSecs suggested backoff, 0 if allowed
 */
public record RateLimitResult(boolean allowed, long remaining, long limit, long retryAfterSecs) {

    public static RateLimitResult allow(long remaining, long limit) {
        return new RateLimitResult(true, remaining, limit, 0);
    }

    public static RateLimitResult deny(long limit, long retryAfterSecs) {
        return new RateLimitResult(false, 0, limit, retryAfterSecs);
    }
}
