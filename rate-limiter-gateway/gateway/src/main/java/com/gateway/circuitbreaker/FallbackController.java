package com.gateway.circuitbreaker;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Reached when Resilience4j trips the circuit breaker for a route (error-rate
 * threshold exceeded). Returns a fast, cheap response instead of letting
 * requests queue up against a failing backend.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Upstream service temporarily unavailable",
                        "circuitBreaker", "open"
                )));
    }
}
