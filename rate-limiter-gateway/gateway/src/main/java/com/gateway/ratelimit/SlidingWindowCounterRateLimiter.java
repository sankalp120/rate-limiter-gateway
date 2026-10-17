package com.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    public SlidingWindowCounterRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                                            @Qualifier("slidingWindowCounterScript") RedisScript<List> script) {
        this.redisTemplate = redisTemplate;
        this.script = script;
    }

    @Override
    public Mono<RateLimitResult> tryAcquire(String key, long limit, long windowSecs) {
        long nowSecs = Instant.now().getEpochSecond();
        List<String> keys = List.of("rl:swc:" + key);
        List<String> args = List.of(
                String.valueOf(limit),
                String.valueOf(windowSecs),
                String.valueOf(nowSecs)
        );

        return redisTemplate.execute(script, keys, args)
                .next()
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<Long> r = (List<Long>) result;
                    boolean allowed = r.get(0) == 1L;
                    long remaining = r.get(1);
                    long retryAfter = r.get(2);
                    return allowed
                            ? RateLimitResult.allow(remaining, limit)
                            : RateLimitResult.deny(limit, retryAfter);
                });
    }

    @Override
    public RateLimitAlgorithm algorithm() {
        return RateLimitAlgorithm.SLIDING_WINDOW_COUNTER;
    }
}
