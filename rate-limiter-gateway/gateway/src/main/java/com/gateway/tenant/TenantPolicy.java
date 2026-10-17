package com.gateway.tenant;

import com.gateway.ratelimit.RateLimitAlgorithm;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tenant_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPolicy {

    @Id
    @Column(name = "api_key", nullable = false, updatable = false)
    private String apiKey;

    @Column(nullable = false)
    private String tenantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RateLimitAlgorithm algorithm;

    /** Requests allowed per window (or bucket capacity for token bucket). */
    @Column(nullable = false)
    private long limit;

    /** Window size in seconds. */
    @Column(nullable = false)
    private long windowSecs;

    @Column(nullable = false)
    private boolean blocked;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
