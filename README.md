# Rate Limiter as a Service

A standalone, self-hostable rate limiting microservice built in **Java 21 + Spring Boot 4 + Redis**, plus a small demo application proving it works as real, callable infrastructure — not just an isolated API.

Any app, company, or team can plug into this service to add rate limiting to their backend without building it themselves. Call one REST endpoint, get an allow/deny decision back.

---

## Architecture

```
                 POST /demo/otp/send
   Client  ──────────────────────────▶  demo-otp-service  (:8081)
                                               │
                                               │ POST /api/v1/check
                                               ▼
                                      rate-limiter-service  (:8080)
                                               │
                                               │ Redis protocol
                                               ▼
                                             Redis  (:6379)
```

Three independent processes, cooperating over real network calls — not a single monolith. `demo-otp-service` exists specifically to prove the rate limiter is genuinely usable by any external service, the way it would be in a real system.

---

## Quick Start

Requires only [Docker](https://www.docker.com/products/docker-desktop/) — no local Java or Maven needed.

```bash
git clone https://github.com/nikhil0306/rate-limiter-service.git
cd rate-limiter-service
docker compose up --build
```

Once all three containers report healthy, try it:

```bash
# Configure a rule: 3 requests per 60 seconds for the "OTP" action
curl -X POST http://localhost:8080/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{"action":"OTP","limit":3,"windowSeconds":60,"algorithm":"sliding_window"}'

# Simulate a login flow through the demo service
curl -X POST http://localhost:8081/demo/otp/send \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123"}'
```

Call the second command 4 times in a row — the first 3 succeed, the 4th is denied with a retry time.

---

## Projects in This Repository

| Project | Purpose | README |
|---|---|---|
| [`rate-limiter-service`](./rate-limiter-service) | The core rate limiter — algorithms, rules, REST API | [Read more →](./rate-limiter-service/README.md) |
| [`demo-otp-service`](./demo-otp-service) | A small OTP login service that calls the rate limiter over HTTP | [Read more →](./demo-otp-service/README.md) |

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Core language |
| Spring Boot 4.1 | REST API framework, dependency injection, config |
| Redis 7 | In-memory counter storage with TTL |
| Spring Data Redis | Redis integration |
| JUnit 5 + Mockito | Unit and slice testing |
| Docker + Docker Compose | One-command deployment for the whole system |
| Maven | Build tool and dependency management |

---

## Resume Line

> Designed and open-sourced a Rate Limiter as a Service using Java 21, Spring Boot, and Redis — implementing Token Bucket and Sliding Window Counter algorithms via the Strategy Pattern. Exposes REST APIs for real-time allow/deny decisions, configurable rules per action, and usage monitoring. Includes a companion demo service proving real cross-service integration, and is fully containerized with Docker for one-command self-hosting.

---

## License

MIT
