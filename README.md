1. Spring Cloud Gateway — the entry point

Receives HTTP requests, matches routes, applies filters and forwards requests to the appropriate backend. Filters can handle authentication, logging, rate limiting and other cross-cutting concerns.

Interview question: Why use an API gateway instead of letting clients call every microservice directly?




2. Multi-tenancy

The system identifies which customer a request belongs to and applies that customer's policies. Tenant identity must come from a trusted authentication mechanism or a validated credential, not an arbitrary client-supplied header.

Interview question: How do you prevent Tenant A from using Tenant B's configuration?




3. Rate limiting — Token Bucket and Sliding Window

Controls how many requests a tenant can make over time. Token Bucket allows short bursts within a configured capacity; Sliding Window tracks traffic over a moving time interval.

Interview question: How do the algorithms differ, and which would you choose for a given traffic pattern?




4. Redis and Lua scripts

Redis stores shared rate-limiting state. Lua scripts can execute related Redis operations atomically, preventing concurrent requests from separately reading and modifying the same counter or token balance.

Interview question: Why is an in-memory counter inside one Java process insufficient when multiple gateway instances run?




5. JWT and the admin API

JSON Web Tokens carry claims that can be verified by the server. An administrative API could let authorised administrators create tenants, change limits or inspect configuration.

Interview question: What is the difference between authentication and authorisation?




6. Resilience4j circuit breakers

A circuit breaker temporarily stops calls to a failing dependency after configured failure conditions are met. It can later allow test requests to determine whether the dependency has recovered.

Interview question: How is a circuit breaker different from a retry mechanism?




7. Prometheus, Grafana, Docker and k6

Prometheus collects metrics, Grafana visualises them, Docker packages services into containers, and k6 generates traffic for performance testing.

Interview question: How would you prove that your gateway handles concurrent requests correctly and meets a latency target?
