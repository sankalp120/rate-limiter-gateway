package com.gateway.ratelimit;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RateLimiterDispatcher {

    private final Map<RateLimitAlgorithm, RateLimiter> limitersByAlgorithm;

    public RateLimiterDispatcher(List<RateLimiter> limiters) {
        this.limitersByAlgorithm = limiters.stream()
                .collect(Collectors.toMap(RateLimiter::algorithm, l -> l));
    }

    public Mono<RateLimitResult> check(RateLimitAlgorithm algorithm, String key, long limit, long windowSecs) {
        RateLimiter limiter = limitersByAlgorithm.get(algorithm);
        if (limiter == null) {
            return Mono.error(new IllegalStateException("No RateLimiter registered for " + algorithm));
        }
        return limiter.tryAcquire(key, limit, windowSecs);
    }
}
