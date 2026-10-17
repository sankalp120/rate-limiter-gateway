package com.gateway.filter;

import com.gateway.ratelimit.RateLimitResult;
import com.gateway.ratelimit.RateLimiterDispatcher;
import com.gateway.tenant.TenantPolicy;
import com.gateway.tenant.TenantPolicyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Runs before routing. Every request must carry an X-API-Key header that
 * maps to a TenantPolicy; unknown/blocked/over-limit requests are rejected
 * here so they never reach a backend. This is the single choke point for
 * multi-tenant isolation.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final TenantPolicyService tenantPolicyService;
    private final RateLimiterDispatcher dispatcher;
    private final Counter allowedCounter;
    private final Counter rejectedCounter;

    public RateLimitGlobalFilter(TenantPolicyService tenantPolicyService,
                                  RateLimiterDispatcher dispatcher,
                                  MeterRegistry meterRegistry) {
        this.tenantPolicyService = tenantPolicyService;
        this.dispatcher = dispatcher;
        this.allowedCounter = Counter.builder("gateway.requests.allowed")
                .description("Requests that passed rate limiting")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("gateway.requests.rejected")
                .description("Requests rejected by rate limiting or blocking")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
        }

        Optional<TenantPolicy> policyOpt = tenantPolicyService.resolve(apiKey);
        if (policyOpt.isEmpty()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Unknown API key");
        }

        TenantPolicy policy = policyOpt.get();
        if (policy.isBlocked()) {
            rejectedCounter.increment();
            return reject(exchange, HttpStatus.FORBIDDEN, "Tenant is blocked");
        }

        // Rate-limit key is scoped per tenant + route so limits don't bleed
        // across unrelated backends for the same API key.
        String routeKey = policy.getApiKey() + ":" + request.getPath().value();

        return dispatcher.check(policy.getAlgorithm(), routeKey, policy.getLimit(), policy.getWindowSecs())
                .flatMap(result -> handleResult(exchange, chain, result));
    }

    private Mono<Void> handleResult(ServerWebExchange exchange, GatewayFilterChain chain, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (result.allowed()) {
            allowedCounter.increment();
            return chain.filter(exchange);
        }

        rejectedCounter.increment();
        response.getHeaders().add("Retry-After", String.valueOf(result.retryAfterSecs()));
        return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\":\"" + message + "\"}";
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Must run before Resilience4j circuit-breaker filter and before routing.
        return -1;
    }
}
