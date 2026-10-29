# ZeroWatch — Phase 2 Runtime Monitoring

ZeroWatch is an isolated Spring Boot module under `zerowatch/`. It accepts versioned JSON events from a controlled demo producer, validates them, stores them in bounded in-memory stores, evaluates deterministic rules, and serves events and evidence-backed alerts. It is independent of the Phase 1 build and does not modify or verify the Phase 1 analyzer. Phase 3 is not implemented.

## Requirements and commands

Use JDK 17 or newer and Maven 3.9 or newer. From the repository root:

```sh
cd zerowatch
mvn test
mvn spring-boot:run
```

The standalone module uses Spring Boot 3.5.6 and targets Java 17. The collector binds to `127.0.0.1` by default and should not be exposed publicly without authentication and transport/access controls.

Submit one structured event as JSON:

```sh
curl -i -H 'Content-Type: application/json' \
  -d '{"schemaVersion":"1.0","timestamp":"2026-10-28T10:00:00Z","serviceName":"zerowatch-demo","eventType":"ACCESS_DENIED","requestId":"demo-1","attributes":{"actorRef":"actor-deadbeef1234","resource":"admin-console"}}' \
  http://127.0.0.1:8080/api/v1/zerowatch/events
```

The successful event response is HTTP 201 and includes the normalized event plus any newly created alerts. Query recent records with a bounded `limit` from 1 to 500:

```sh
curl 'http://127.0.0.1:8080/api/v1/zerowatch/events?limit=100'
curl 'http://127.0.0.1:8080/api/v1/zerowatch/alerts?limit=100'
```

See `src/test/resources/fixtures/` for synthetic JSON sequences. `DemoEventProducer` creates the three-event denied-access example, and the optional `DemoEventSender` posts those events to the loopback API. To run a self-contained synthetic end-to-end demonstration, start the application with:

```sh
mvn spring-boot:run -Dspring-boot.run.arguments=--zerowatch.demo.send=true
```

The sender is disabled by default, submits only synthetic local events, and does not instrument or monitor other applications.

## Event contract and privacy

Events use schema version `1.0`, UTC/offset timestamps parsed as `Instant`, server ingestion timestamps, generated UUID event IDs when omitted, and enumerated event types. The server ignores client severity because it is not a request field. Attribute names are exact allowlists per event type; actor references must be pseudonymous hex IDs, and resource/operation/outcome values are enumerated. Passwords, tokens, session secrets, and raw request bodies have no accepted fields. Unknown top-level JSON fields are rejected. Request and trace IDs are restricted to safe identifier characters.

Request JSON is limited to 16 KiB. Stored events are limited to 10,000 and alerts to 5,000; records older than 24 hours are pruned during event ingestion. Both stores are in memory: records disappear on process restart, and there is no external persistence. Read endpoints return at most 500 items. Logs include generated event/alert IDs, event type, service name, rule, severity, and evidence count; they do not log attributes or payloads.

## Deterministic rules

| Rule | Trigger | Severity | Evidence and suppression |
| --- | --- | --- | --- |
| `zerowatch.access-denied-burst` v1.0.0 | At least three `ACCESS_DENIED` events for one service, pseudonymous actor, and allowlisted resource within five minutes | MEDIUM | Alert includes the matching event IDs and window summary. Dedupe uses a fixed five-minute UTC time bucket per service/actor/resource. Legitimate repeated denials can still alert. |
| `zerowatch.unexpected-raw-sql-operation` v1.0.0 | Demo application reports `SENSITIVE_OPERATION` with `RAW_SQL_EXECUTION` and `ALLOWED` | HIGH | Alert includes the event ID and normalized operation context. Dedupe uses a fixed five-minute UTC bucket per service/operation. A signal is not proof of SQL injection or exploitation. |

Rules operate only on structured event types and allowlisted context, not arbitrary keyword matches. They detect configured behavior; they do not claim to detect every anomaly or zero-day.

## Phase 1 correlation boundary

The event model preserves an optional `findingId`. Alerts include a Phase 1 finding reference only when `Phase1FindingResolver` can resolve that exact ID. The provided default resolver is intentionally empty, so ZeroWatch functions without Phase 1 and does not infer a correlation from filenames, timestamps, or similar text. Replace the resolver adapter only when a reliable Phase 1 metadata source is available. Phase 1 status remains **Implemented Prototype — Verification Pending**.

## API

- `GET /api/v1/zerowatch/health`
- `POST /api/v1/zerowatch/events`
- `GET /api/v1/zerowatch/events?limit=100`
- `GET /api/v1/zerowatch/events/{eventId}`
- `GET /api/v1/zerowatch/alerts?limit=100`
- `GET /api/v1/zerowatch/alerts/{alertId}`

Errors use `{ "error", "message", "timestamp" }` with HTTP 400 for invalid requests, 409 for duplicate event IDs, 413 for oversized payloads, 415 for unsupported media types, 404 for missing records, and 503 when an in-memory store reaches capacity.

## Verification status

JUnit integration tests exercise the HTTP endpoints for event acceptance/retrieval, validation errors, malformed timestamps and event types, oversized payloads, the suspicious sequence and evidence, normal activity, deduplication, optional finding references, sensitive-field rejection, bounded queries, and unknown IDs. Test fixtures are synthetic. Run `mvn test` in this module and record the actual result in `docs/phase2-verification-status.md`.

At implementation time Maven is unavailable in the execution environment, so the Phase 2 tests have not been run. This module is **In Development — Verification Pending**, not accepted as complete. See `docs/initial-git-state.md` for the recorded starting Git state and failed branch creation attempt.
