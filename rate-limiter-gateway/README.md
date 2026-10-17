# Rate Limiter Gateway

A multi-tenant API gateway built on Spring Cloud Gateway with three
distributed rate-limiting algorithms implemented from scratch (not
framework annotations), Redis-backed atomic state via Lua scripting,
Resilience4j circuit breakers, and a JWT-secured admin API for
hot-reloadable per-tenant policies.

## Why this exists

Off-the-shelf gateways (Kong, Cloudflare, `@RateLimiter`) hide the hard
part: keeping rate-limit counters correct when *multiple gateway
instances* hit the same Redis backend concurrently. This project
implements that correctness explicitly, with three different strategies
so you can compare their tradeoffs directly:

| Algorithm | Memory | Precision | Notes |
|---|---|---|---|
| Token Bucket | O(1) | Approx (allows bursts up to capacity) | Best for allowing burst traffic |
| Sliding Window Log | O(limit) | Exact | No boundary burst issue, costs more memory |
| Sliding Window Counter | O(1) | Approx (weighted blend of 2 windows) | Good balance of the two above |

All three are implemented as atomic Redis Lua scripts (`gateway/src/main/resources/scripts/`)
so a single `EVAL` does read-modify-write with no race window, even
under concurrent load from multiple gateway pods.

## Architecture

```
Client --> [Gateway :8080] --> backend-a (:9001) / backend-b (:9002)
              |         |
           Redis     Postgres
        (rate-limit   (tenant
           state)      policies)
```

1. `RateLimitGlobalFilter` runs first on every request, resolves the
   tenant from `X-API-Key`, and calls the configured algorithm via
   `RateLimiterDispatcher`.
2. Allowed requests proceed through Spring Cloud Gateway routing, wrapped
   in a Resilience4j circuit breaker per route (falls back to `/fallback`
   on trip).
3. `TenantPolicyService` caches policies in memory, refreshed every 15s
   and updated instantly on any admin write (so limits change without a
   restart).
4. Everything is scraped by Prometheus / visualized in Grafana via
   Spring Actuator's `/actuator/prometheus` endpoint.

## Run it

```bash
docker compose up --build
```

This starts: gateway (`:8080`), backend-a (`:9001`), backend-b (`:9002`),
Redis (`:6379`), Postgres (`:5432`), Prometheus (`:9090`), Grafana (`:3000`).

A default tenant is seeded automatically: `api-key: demo-key`, 20 req/60s,
token bucket.

## Try it

```bash
# A normal request
curl -H "X-API-Key: demo-key" http://localhost:8080/api/a/health

# Hammer it to see 429s kick in
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: demo-key" http://localhost:8080/api/a/health
done
```

## Admin API

```bash
# Get a JWT
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

# Register a new tenant using the sliding-window-log algorithm
curl -X PUT http://localhost:8080/admin/tenants \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "acme-corp",
    "tenantName": "Acme Corp",
    "algorithm": "SLIDING_WINDOW_LOG",
    "limit": 100,
    "windowSecs": 60
  }'

# List tenants
curl http://localhost:8080/admin/tenants -H "Authorization: Bearer $TOKEN"

# Block a tenant on the fly
curl -X POST http://localhost:8080/admin/tenants/acme-corp/block -H "Authorization: Bearer $TOKEN"

# Live metrics snapshot
curl http://localhost:8080/admin/metrics/summary -H "Authorization: Bearer $TOKEN"
```

## Load testing (proves the distributed-counter claim)

Requires [k6](https://k6.io/):

```bash
k6 run loadtest/rate_limit_test.js
```

This fires 50 req/s against a tenant limited to 20 req/60s and asserts
a meaningful rejection rate — i.e. proves the limiter is actually
enforcing the cap under load, not just returning 200 for everything.

## Testing the circuit breaker

```bash
# Make backend-a fail 80% of the time
docker compose stop backend-a
docker compose run -e FAILURE_RATE=0.8 -p 9001:9001 backend-a

# Hit it repeatedly — after ~10 failures in the 20-request sliding window,
# the breaker opens and you start getting instant /fallback responses
# instead of waiting on a failing backend.
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: demo-key" http://localhost:8080/api/a/health
done
```

Check breaker state directly: `curl http://localhost:8080/actuator/circuitbreakers`

## Project layout

```
gateway/                    Spring Boot gateway application
  src/main/java/com/gateway/
    ratelimit/               3 algorithm implementations + dispatcher
    tenant/                  JPA entity + hot-reloadable cache
    filter/                  Global filter wiring it all into the request path
    circuitbreaker/          Fallback controller
    security/                JWT issuing/validation, WebFlux security config
    admin/                   Admin REST API
  src/main/resources/
    scripts/                 Lua scripts (the atomic core of this project)
    application.yml
backends/backend-a/         Dummy upstream (stdlib Python, fault injection via env vars)
backends/backend-b/         Same, second instance
observability/prometheus.yml
loadtest/rate_limit_test.js k6 script
docker-compose.yml
```

## What to say about this in an interview

- **Why Lua scripts instead of `INCR` + `EXPIRE`**: separate commands
  aren't atomic — two concurrent requests can both read the count before
  either increments it, letting both through when only one should pass.
  A single `EVAL` is executed atomically by Redis's single-threaded
  command loop, closing that race.
- **Why three algorithms**: token bucket allows controlled bursts (good
  for bursty client behavior like page loads), sliding window log is
  exact but O(n) memory, sliding window counter approximates the log's
  precision at O(1) memory — a genuine engineering tradeoff, not just
  "pick one."
- **Hot-reload without restart**: policies live in Postgres, cached
  in-memory, updated synchronously on admin writes and periodically
  reconciled — so a config change takes effect on the next request, and
  multiple gateway instances converge within one refresh interval.
