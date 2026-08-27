# demo-otp-service

A small, independent Spring Boot application that simulates an OTP login flow, calling [`rate-limiter-service`](../rate-limiter-service) over real HTTP to decide whether to "send" the OTP.

## Why This Exists

It's one thing to prove a rate limiter's own tests pass. It's another to prove it's genuinely usable as shared infrastructure. This service exists purely to demonstrate that: it holds no state of its own, knows nothing about Redis or rate-limiting algorithms, and makes exactly one decision per request by asking another service a question over the network — the same way any real consumer of `rate-limiter-service` would.

## API

### `POST /demo/otp/send`

**Request**
```json
{ "userId": "user123" }
```

**Response — 200 OK**
```
OTP sent to user123
```

**Response — 429 Too Many Requests**
```
Too many attempts. Try again in 42 seconds.
```

Plain text responses, not JSON — this is a thin demo layer, not an API meant for further integration.

## How It Calls the Rate Limiter

`RateLimiterClient` uses Spring's `RestClient` to call `POST /api/v1/check` on the main service, configured via the `ratelimiter.base-url` property. It defines its own local copies of the request/response shapes it needs (`RateLimitCheckRequest`, `OtpCheckResult`) rather than importing the main service's classes directly — the two services share only an HTTP contract, not code, exactly like two independently deployable microservices should.

## Running Locally

**Option A — Docker, from the repo root:**
```bash
docker compose up --build
```

**Option B — Maven, with the main service already running on port 8080:**
```bash
mvn spring-boot:run
```
Starts on port `8081`.

## Testing

```bash
mvn test
```
2 tests, `@WebMvcTest` slice tests with the rate limiter client mocked — no network call, no dependency on the main service actually running.
