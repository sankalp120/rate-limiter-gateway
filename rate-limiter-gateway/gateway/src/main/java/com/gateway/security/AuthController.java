package com.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Minimal login endpoint for issuing admin JWTs. In a real deployment this
 * would delegate to a proper identity provider / user store; here it checks
 * against a single configured admin credential so the gateway is self-contained.
 */
@RestController
public class AuthController {

    private final JwtService jwtService;
    private final String adminUser;
    private final String adminPassword;

    public AuthController(JwtService jwtService,
                           @Value("${gateway.security.admin-username}") String adminUser,
                           @Value("${gateway.security.admin-password}") String adminPassword) {
        this.jwtService = jwtService;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String tokenType) {}

    @PostMapping("/auth/login")
    public Mono<ResponseEntity<?>> login(@RequestBody LoginRequest request) {
        if (adminUser.equals(request.username()) && adminPassword.equals(request.password())) {
            String token = jwtService.issueToken(request.username(), "ADMIN");
            return Mono.just(ResponseEntity.ok(new LoginResponse(token, "Bearer")));
        }
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials")));
    }
}
