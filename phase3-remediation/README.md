# ZeroTrace AI Phase 3 — ZeroRemediate

This is a standalone Spring Boot module for a reviewable finding → remediation proposal → controlled verification workflow. It does not depend on or modify Phase 1 or ZeroWatch.

## Current MVP and provider status

The default provider is a deterministic **FIXTURE** provider for one exact synthetic SQL injection example. It is not an AI model and never claims a live model was called. Set `ZEROREMEDIATE_PROVIDER=none` to make proposals explicitly unavailable (HTTP 503). `RemediationProvider` is the seam for a future provider; no live model integration or API key is implemented.

The proposal returns an explanation, rationale, assumptions, suggested regression test, proposed snippet, and a single-file unified diff. The API never applies the patch. Finding paths are validated as relative paths and are treated only as labels; no source path is opened. Finding evidence and code context are not logged.

## API

- `POST /api/v1/findings` — submit a bounded JSON finding; returns `201`.
- `GET /api/v1/findings/{id}` — retrieve the in-memory finding.
- `POST /api/v1/findings/{id}/proposals` — produce a fixture proposal (or `503` if provider disabled).
- `GET /api/v1/proposals/{id}` — retrieve proposal and diff.
- `POST /api/v1/proposals/{id}/verify` — run the fixed in-process SQL fixture checks.
- `GET /api/v1/verifications/{id}` — retrieve the recorded report.

Input supports rule `zerotrace.java.sql-injection` and category `sql-injection`, severities `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`. Finding submissions require a known `Content-Length` and are capped at 16 KiB; chunked/unknown-length finding requests are rejected. The in-memory audit store is bounded and resets on process restart.

## Verification boundary and threat model

Provider output is untrusted and checked for bounded fields and a one-file patch targeting only the submitted relative path. It is displayed as text and never executed or applied. Verification runs trusted Java predicates over the exact synthetic vulnerable and safe snippets, with a bounded timeout. It runs no shell command, user code, model-generated test, Semgrep scan, build, or repository test. `VERIFIED` therefore means only that the two named synthetic fixture checks passed; it is not evidence that a real repository finding is fixed. `FAILED` means one defined check failed; `INCONCLUSIVE` means the checks were absent, timed out, or otherwise incomplete.

The API binds to loopback (`127.0.0.1:8083`). Storage is in-memory and intended for local demonstration only; there is no authentication, durable audit store, or live model provider.

## Run

From repository root, with JDK 17+ and Maven available:

```powershell
mvn -f phase3-remediation/pom.xml clean test
mvn -f phase3-remediation/pom.xml spring-boot:run
```

For no provider: `$env:ZEROREMEDIATE_PROVIDER='none'`. The default fixture provider should be described as fixture output in demonstrations.

## Verification record

See [docs/phase3-verification-status.md](docs/phase3-verification-status.md) for the actual execution status and limitations.
