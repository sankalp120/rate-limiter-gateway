package com.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    public TokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                                   @Qualifier("tokenBucketScript") RedisScript<List> script) {
        this.redisTemplate = redisTemplate;
        this.script = script;
    }

    @Override
    public Mono<RateLimitResult> tryAcquire(String key, long limit, long windowSecs) {
        // refill_rate = capacity / window -> tokens/sec so the bucket fully
        // refills once per configured window, e.g. 100 req / 60s window.
        double refillRate = windowSecs <= 0 ? limit : (double) limit / windowSecs;
        double now = Instant.now().toEpochMilli() / 1000.0;

        List<String> keys = List.of("rl:tb:" + key);
        List<String> args = List.of(
                String.valueOf(limit),
                String.valueOf(refillRate),
                String.valueOf(now),
                "1"
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
        return RateLimitAlgorithm.TOKEN_BUCKET;
    }
}
