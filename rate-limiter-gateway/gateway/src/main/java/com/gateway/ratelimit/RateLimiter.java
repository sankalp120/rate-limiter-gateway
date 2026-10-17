package com.gateway.ratelimit;

import reactor.core.publisher.Mono;

/**
 * Contract for a distributed rate-limiting strategy.
 * Every implementation must be safe under concurrent execution
 * across multiple gateway instances sharing the same Redis backend.
 */
public interface RateLimiter {

    /**
     * @param key        unique key identifying the tenant + route being limited
     * @param limit      max requests allowed within the window/bucket
     * @param windowSecs window size in seconds (or refill window for token bucket)
     */
    Mono<RateLimitResult> tryAcquire(String key, long limit, long windowSecs);

    RateLimitAlgorithm algorithm();
}
