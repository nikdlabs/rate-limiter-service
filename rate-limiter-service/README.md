# rate-limiter-service

The core rate limiting engine. A standalone Spring Boot service that any application can call to get an allow/deny decision for a given user and action, backed by Redis.

## Why This Exists

Every backend system needs rate limiting — to stop OTP spam, brute-force login attempts, and general API abuse. Most small teams either skip it or rebuild it inside every service that needs it. This project is shared infrastructure instead: one service, called by anything, the same way a company might run one central rate limiter across all of its microservices.

## Why a Service, Not a Library

A library means every consuming service maintains its own counters — they never coordinate, so a user can bypass a limit just by hitting a different service. A standalone service, shared across everything, closes that gap.

---

## API Reference

Base path: `/api/v1`

### `POST /check`

The core decision endpoint.

**Request**
```json
{ "userId": "user123", "action": "OTP" }
```

**Response — 200 OK (allowed)**
```json
{
  "allowed": true,
  "remaining": 4,
  "resetAfter": 42,
  "algorithm": "sliding_window"
}
```

**Response — 429 Too Many Requests (denied)**
```json
{
  "allowed": false,
  "remaining": 0,
  "retryAfter": 42,
  "reason": "OTP limit exceeded"
}
```

### `POST /rules`

Configure a rate limit rule for an action.

**Request**
```json
{ "action": "OTP", "limit": 5, "windowSeconds": 60, "algorithm": "sliding_window" }
```
`algorithm` is either `"sliding_window"` or `"token_bucket"`. Returns `201 Created` with the saved rule.

### `GET /rules`

Lists every configured rule as a JSON array.

### `GET /usage/{userId}`

Reports current standing across every configured action for a user — remaining requests, reset time, limit — without consuming any quota.

```json
[
  { "action": "OTP", "limit": 5, "remaining": 4, "resetAfterSeconds": 42, "algorithm": "sliding_window" }
]
```

### `DELETE /usage/{userId}`

Resets a user's counters across every configured action. Returns `204 No Content`.

### `GET /actuator/health`

Standard health check, including live Redis connectivity status.

---

## The Two Algorithms

### Sliding Window Counter

Blends the current time window's request count with the *previous* window's count, weighted by how far into the current window "now" is:

```
weightOfPrevious = 1 - (elapsedInCurrentWindow / windowSeconds)
estimatedCount   = (previousWindowCount × weightOfPrevious) + currentWindowCount
```

This avoids the classic flaw of a naive fixed-window counter, where a user could burst up to 2× the limit right at a window boundary. Best for strict, security-sensitive actions — login attempts, OTP requests.

### Token Bucket

Each user has a bucket holding up to `limit` tokens. Every request consumes one; tokens refill continuously over time (`limit / windowSeconds` per second), computed lazily on each read rather than via a background job. Allows short bursts for users who've been idle — better UX for general API traffic.

Both algorithms implement the same `RateLimitAlgorithm` interface (the **Strategy Pattern**) — Spring auto-wires a `Map<String, RateLimitAlgorithm>` keyed by algorithm name, so picking the right one for a given rule is a single map lookup, with zero conditional branching anywhere in the codebase.

---

## Architecture

```
com.ratelimiter
├── controller   → HTTP request/response shaping only
├── service      → business logic, orchestration (RateLimiterService, RuleService)
├── algorithm    → the rate-limiting math (Strategy Pattern)
├── model        → DTOs and data shapes
└── config       → Redis connection, Clock bean
```

Each layer has exactly one responsibility. The controller never touches Redis; the algorithm never reads an HTTP header.

**Why Redis over a relational database:** sub-millisecond reads/writes, an atomic `INCR` command that eliminates race conditions without any application-level locking, and native TTL-based expiry that replaces what would otherwise be a scheduled cleanup job.

---

## Running Locally

**Option A — Docker only, from the repo root:**
```bash
docker compose up --build
```

**Option B — Maven + a local Redis container:**
```bash
docker run -d -p 6379:6379 --name redis redis:7-alpine
mvn spring-boot:run
```
The app starts on port `8080`.

---

## Testing

```bash
mvn test
```

35 tests across every layer — algorithm unit tests (Mockito-mocked Redis), service-layer tests, and `@WebMvcTest` controller slice tests via `MockMvc`. No test requires a real Redis connection except the single Spring context smoke test.

---

## Key Design Decisions

**Redis Set as an index, not the `KEYS` command.** Listing all configured rules needs to enumerate every rule key. `KEYS rule:*` would scan the entire keyspace and block Redis's single command thread — a well-known production anti-pattern. Instead, a dedicated Redis Set (`rule:index`) tracks every configured action name, giving O(1) membership lookups regardless of dataset size.

**A real bug, found by actually using the app, fixed properly.** The original Sliding Window implementation keyed its counter to `currentTime / windowSeconds` — an epoch-aligned *fixed* window, not a true sliding one, despite the class name. Manual testing surfaced the flaw directly: right at a window boundary, a user could burst up to 2× their configured limit in a few real seconds. The fix — the weighted blend described above — is the same technique production systems like Cloudflare use, verified with a deterministic regression test using an injected `Clock` rather than the real system clock, so the exact boundary scenario can be reproduced on demand.

**Explicit `DEL`, used deliberately once.** Every other Redis interaction in this project relies on TTL for passive, automatic cleanup. The one exception is the admin-triggered `DELETE /usage/{userId}` reset — a genuinely different kind of operation (immediate, deliberate) that correctly calls for immediate, deliberate deletion instead of waiting on a timer.

**Manual JSON serialization, not Redis's built-in object serializers.** Spring Data Redis 4.x deprecated its JSON-based Redis serializers over deserialization security concerns. Rules are instead serialized to plain JSON strings explicitly via Jackson before storage — visible, explicit, and under full control rather than hidden framework magic.

---

## Project Structure

```
rate-limiter-service/
├── src/main/java/com/ratelimiter/
│   ├── RateLimiterServiceApplication.java
│   ├── controller/RateLimiterController.java
│   ├── service/{RateLimiterService,RuleService}.java
│   ├── algorithm/{RateLimitAlgorithm,SlidingWindowAlgorithm,TokenBucketAlgorithm}.java
│   ├── model/{RuleConfig,RateLimitResult,UsageInfo,CheckRequest,CheckResponse}.java
│   └── config/{RedisConfig,AppConfig}.java
├── src/test/java/com/ratelimiter/...
├── Dockerfile
└── pom.xml
```
