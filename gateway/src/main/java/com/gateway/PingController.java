package com.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class PingController {

    // Mono<T> = "one value, delivered later" (reactive, non-blocking).
    @GetMapping("/ping")
    public Mono<Map<String, String>> ping() {
        return Mono.just(Map.of("status", "ok", "service", "rate-limiter-gateway"));
    }
}
