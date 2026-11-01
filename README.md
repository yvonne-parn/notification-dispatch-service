# Homework - Notification Dispatch Service

An application that sends EMAIL, SMS and PUSH notifications, records every attempt, and rejects blocked or rate-limited recipients and channels that keep failing.

Java 17, Spring Boot 4.1, H2 (in memory), Flyway.

## Run

```bash
./gradlew bootRun
```
```bash
./gradlew test
```

- POST /api/notifications/send    {"type":"EMAIL","target":"test@example.com","payload":"hi"}
- GET  /api/notifications/latest
- GET  /api/notifications/history?page=0&size=20

Types: EMAIL, SMS, PUSH

## Problems found in the legacy code

- The circuit breaker never recovered. After 3 failures every request was skipped, and only a successful send reset it.
- The circuit breaker was global, so one channel's failures blocked the others.
- Ids were `max(id) + 1`, which only worked because the whole method was `synchronized`, so every send waited for every other send.
- "Blocked" and "error" were substring checks in production code.
- Everything failed as a 500, and `schema.sql` dropped the table on every start.

## Behavior kept

- 3 requests per minute are accepted for a recipient, further requests are rejected.
- Blocked recipients and unsupported types are rejected.
- Failed attempts are saved.
- 3 failures in a row open the circuit breaker.

## Behavior changed

- Unknown type fails with `400` and nothing is saved (before: `500`, saved with status `U`).
- Blocked recipients fail with `403` and rate limited requests with `429`.
- Breaker open returns `503`, and it closes again after 5 minutes.
- Provider failure returns `502`, and the failed attempt is saved.
- `/latest` with no data returns `404` instead of `200` with an empty body.
- The path moved from `/api/legacy/notifications` to `/api/notifications`.

The breaker is per channel and opens after 3 failures in a row. One success resets it, as before.

## Design choices

- Blocked targets come from config (exact match, lowercase). A table with endpoints would be better, but I didn't build it.
- One exception class carries the status, instead of one class per error.
- Each channel is a class behind an interface, so a new channel doesn't change the service.
- Flyway manages the schema, so restarting doesn't delete data. The old `ntf_tx` table is dropped without migrating data, since the old database was in memory and started empty.
- No `@Transactional` on send. The FAILED row has to stay saved after the 502.
- Payloads are never logged or returned. Targets aren't logged either.

## Not done

- No real email, SMS or push provider.
- The bonus tasks (update endpoint, blacklist table).
- The rate limit can be exceeded if many requests for the same recipient arrive at the same moment.
- Sending happens inside the request. A queue with background workers would be better for production.
