package com.gateway.admin;

import com.gateway.ratelimit.RateLimitAlgorithm;
import com.gateway.tenant.TenantPolicy;
import com.gateway.tenant.TenantPolicyService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Search;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TenantPolicyService tenantPolicyService;
    private final MeterRegistry meterRegistry;

    public AdminController(TenantPolicyService tenantPolicyService, MeterRegistry meterRegistry) {
        this.tenantPolicyService = tenantPolicyService;
        this.meterRegistry = meterRegistry;
    }

    public record TenantPolicyRequest(
            @NotBlank String apiKey,
            @NotBlank String tenantName,
            @NotNull RateLimitAlgorithm algorithm,
            @Positive long limit,
            @Positive long windowSecs
    ) {}

    @GetMapping("/tenants")
    public Map<String, TenantPolicy> listTenants() {
        return tenantPolicyService.snapshot();
    }

    @PutMapping("/tenants")
    public ResponseEntity<TenantPolicy> upsertTenant(@RequestBody TenantPolicyRequest req) {
        TenantPolicy policy = TenantPolicy.builder()
                .apiKey(req.apiKey())
                .tenantName(req.tenantName())
                .algorithm(req.algorithm())
                .limit(req.limit())
                .windowSecs(req.windowSecs())
                .blocked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return ResponseEntity.ok(tenantPolicyService.upsert(policy));
    }

    @DeleteMapping("/tenants/{apiKey}")
    public ResponseEntity<Void> deleteTenant(@PathVariable String apiKey) {
        tenantPolicyService.delete(apiKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{apiKey}/block")
    public ResponseEntity<Void> blockTenant(@PathVariable String apiKey) {
        tenantPolicyService.setBlocked(apiKey, true);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/tenants/{apiKey}/unblock")
    public ResponseEntity<Void> unblockTenant(@PathVariable String apiKey) {
        tenantPolicyService.setBlocked(apiKey, false);
        return ResponseEntity.ok().build();
    }

    /** Quick live snapshot without needing a full Prometheus/Grafana round trip. */
    @GetMapping("/metrics/summary")
    public Map<String, Object> metricsSummary() {
        double allowed = counterValue("gateway.requests.allowed");
        double rejected = counterValue("gateway.requests.rejected");
        return Map.of(
                "requestsAllowed", allowed,
                "requestsRejected", rejected,
                "rejectionRate", (allowed + rejected) == 0 ? 0 : rejected / (allowed + rejected),
                "tenantCount", tenantPolicyService.snapshot().size()
        );
    }

    private double counterValue(String name) {
        return Search.in(meterRegistry).name(name).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }
}
