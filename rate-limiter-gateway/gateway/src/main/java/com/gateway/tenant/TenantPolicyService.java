package com.gateway.tenant;

import com.gateway.ratelimit.RateLimitAlgorithm;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Policies are cached in-memory so the gateway never hits Postgres on the
 * request hot path. The cache is refreshed on a schedule AND updated
 * synchronously on every admin write, so changes apply without a restart
 * (that's the "hot-reloadable" requirement) while a periodic refresh
 * keeps multiple gateway instances eventually consistent with each other.
 */
@Service
public class TenantPolicyService {

    private static final Logger log = LoggerFactory.getLogger(TenantPolicyService.class);

    private final TenantPolicyRepository repository;
    private final Map<String, TenantPolicy> cache = new ConcurrentHashMap<>();

    public TenantPolicyService(TenantPolicyRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        reload();
        if (repository.count() == 0) {
            seedDefaultPolicy();
        }
    }

    @Scheduled(fixedDelayString = "${gateway.tenant-policy.refresh-interval-ms:15000}")
    public void reload() {
        Map<String, TenantPolicy> fresh = new ConcurrentHashMap<>();
        repository.findAll().forEach(p -> fresh.put(p.getApiKey(), p));
        cache.clear();
        cache.putAll(fresh);
        log.debug("Tenant policy cache refreshed, {} tenants loaded", cache.size());
    }

    public Optional<TenantPolicy> resolve(String apiKey) {
        return Optional.ofNullable(cache.get(apiKey));
    }

    public TenantPolicy upsert(TenantPolicy policy) {
        Instant now = Instant.now();
        policy.setUpdatedAt(now);
        if (policy.getCreatedAt() == null) {
            policy.setCreatedAt(now);
        }
        TenantPolicy saved = repository.save(policy);
        cache.put(saved.getApiKey(), saved); // immediate hot-reload for this key
        return saved;
    }

    public void delete(String apiKey) {
        repository.deleteById(apiKey);
        cache.remove(apiKey);
    }

    public void setBlocked(String apiKey, boolean blocked) {
        cache.computeIfPresent(apiKey, (k, v) -> {
            v.setBlocked(blocked);
            repository.save(v);
            return v;
        });
    }

    public Map<String, TenantPolicy> snapshot() {
        return Map.copyOf(cache);
    }

    private void seedDefaultPolicy() {
        TenantPolicy demo = TenantPolicy.builder()
                .apiKey("demo-key")
                .tenantName("demo-tenant")
                .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
                .limit(20)
                .windowSecs(60)
                .blocked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        upsert(demo);
        log.info("Seeded default tenant policy for api-key 'demo-key' (20 req/60s, TOKEN_BUCKET)");
    }
}
