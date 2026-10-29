# ZeroTrace AI
### A three-phase application security workbench

ZeroTrace AI is a prototype application security platform designed to connect three stages of security work: **static source-code analysis, runtime-event monitoring, and remediation proposal review**.

The project aims to help developers and security reviewers move from a suspicious code pattern to runtime evidence and then to a proposed corrective change—all through one interface.

> **Project status:** Hackathon prototype. Static analysis uses Semgrep, ZeroWatch uses explicit rule-based event correlation, and ZeroRemediate currently uses a deterministic fixture provider. The current implementation does not provide universal zero-day detection, live application instrumentation, or automatic production code repair.

---

## Table of contents

- [The problem](#the-problem)
- [Our approach](#our-approach)
- [Phase 1 — ZeroTrace source analysis](#phase-1--zerotrace-source-analysis)
- [Phase 2 — ZeroWatch runtime-event monitoring](#phase-2--zerowatch-runtime-event-monitoring)
- [Phase 3 — ZeroRemediate](#phase-3--zeroremediate)
- [How the phases fit together](#how-the-phases-fit-together)
- [Technology stack](#technology-stack)
- [Architecture and ports](#architecture-and-ports)
- [Prerequisites](#prerequisites)
- [Installation and local setup](#installation-and-local-setup)
- [Using the application](#using-the-application)
- [API reference](#api-reference)
- [Testing](#testing)
- [Security and privacy considerations](#security-and-privacy-considerations)
- [Current limitations](#current-limitations)
- [Future improvements](#future-improvements)
- [Contributing](#contributing)

---

## The problem

Application security work often involves separate tools and disconnected workflows.

1. **Source-code review:** A scanner identifies a potentially unsafe pattern, but a finding may need further investigation to establish whether it is exploitable.
2. **Runtime investigation:** Security teams need evidence about application behavior, such as repeated access denials or sensitive operations.
3. **Remediation:** Developers need a concrete explanation of the issue and a proposed fix that they can review and test.

These stages are related, but findings, runtime events, and corrective changes may be difficult to investigate together.

ZeroTrace AI explores a unified workflow for these tasks. Its current prototype demonstrates selected examples of the workflow rather than claiming to solve every application security problem.

## Our approach

ZeroTrace AI has three components:

| Phase | Component | Main responsibility |
|---|---|---|
| 1 | **ZeroTrace** | Scan uploaded Java source archives and return static-analysis findings. |
| 2 | **ZeroWatch** | Accept structured runtime events and generate alerts when configured detection rules match. |
| 3 | **ZeroRemediate** | Generate a remediation proposal for a supported fixture and run synthetic verification checks. |

The React interface brings the components together in one local workbench. Each phase can also be investigated through its API.

---

## Phase 1 — ZeroTrace source analysis

### Purpose

ZeroTrace analyzes an uploaded ZIP archive containing Java source files. It uses Semgrep-based static analysis to identify code patterns that match the configured rules.

Static analysis examines source code without needing to execute the application.

### Workflow

1. A developer selects a ZIP archive containing Java source files.
2. The frontend sends the archive to the Phase 1 API.
3. The backend validates the upload and invokes the configured analysis engine.
4. The API returns scan information and findings.
5. The dashboard displays the scan status, analyzed-file count, and finding details.

### What the user can inspect

- Scan status and scan identifier.
- Number of files analyzed.
- Findings returned by the scanner.
- Rule identifiers, severity, source location, and available descriptions or evidence.

### Why this matters

A source scanner can help reviewers prioritize potentially risky code before deployment. However, a matched pattern is not automatically proof that a vulnerability is exploitable. Findings require context and human review.

### Main API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/health` | Check the Phase 1 service. |
| `POST` | `/api/v1/scans` | Submit a source ZIP for scanning. |
| `GET` | `/api/v1/scans/{id}` | Retrieve scan information. |
| `GET` | `/api/v1/scans/{id}/findings` | Retrieve findings for a scan. |

The upload endpoint accepts a multipart file field named `file`.

---

## Phase 2 — ZeroWatch runtime-event monitoring

### Purpose

ZeroWatch accepts structured application-event records and evaluates them against explicit detection rules. It demonstrates how repeated behavior can be correlated into a security alert.

The current demonstration uses synthetic events submitted by the frontend or through HTTP requests. It does not automatically instrument a live application or collect operating-system telemetry.

### Event validation

The event API uses a defined schema and validates fields such as:

- Schema version.
- Timestamp.
- Service name.
- Event type.
- Optional request, trace, and finding identifiers.
- A bounded set of event attributes.

The event validator restricts accepted values for fields such as actor references, resources, and sensitive operations. Actor references use pseudonymous identifiers in the demo.

### Demonstrated detection rule

The current demonstration includes the rule:

`zerowatch.access-denied-burst`

The rule generates a medium-severity alert when **three or more access-denied events for the same pseudonymous actor and resource occur within a five-minute window**, subject to the backend's configured conditions.

The alert includes:

- Rule identifier and version.
- Severity and status.
- IDs of the supporting events.
- Detection time.
- An explanation of the observed behavior.
- Evidence describing the matched threshold.

### Example demonstration

1. Submit an `ACCESS_DENIED` event for a pseudonymous actor accessing `admin-console`.
2. Submit a second event for the same actor and resource.
3. Submit a third event within five minutes.
4. Retrieve the alert and inspect the evidence event IDs.

The third event should trigger the configured burst rule when the event sequence satisfies the rule conditions.

This demonstrates rule-based event correlation, not universal attack detection.

### Main API endpoints

The Phase 2 base path is `/api/v1/zerowatch`.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/zerowatch/health` | Check ZeroWatch service status. |
| `POST` | `/api/v1/zerowatch/events` | Submit a structured runtime event. |
| `GET` | `/api/v1/zerowatch/events` | List recent events. |
| `GET` | `/api/v1/zerowatch/events/{eventId}` | Retrieve one event. |
| `GET` | `/api/v1/zerowatch/alerts` | List recent alerts. |
| `GET` | `/api/v1/zerowatch/alerts/{alertId}` | Retrieve one alert. |

The list endpoints accept a `limit` query parameter.

---

## Phase 3 — ZeroRemediate

### Purpose

ZeroRemediate demonstrates the remediation-review stage. It accepts a finding, generates a proposal for a supported example, and exposes a verification workflow.

The current configuration uses a **deterministic fixture provider**. It does not use a live generative AI model to produce arbitrary fixes.

### Demonstrated scenario: SQL injection

The example uses Java code that concatenates untrusted input into an SQL query.

The proposed correction uses a parameterized query with `PreparedStatement` and a bound parameter. This helps keep user input separate from SQL syntax.

### Workflow

1. Submit a supported finding and its bounded code context.
2. Request a remediation proposal.
3. Inspect the root-cause explanation.
4. Review the suggested Java code and unified diff.
5. Request verification against the configured synthetic checks.
6. Inspect the verification response.

### Important boundaries

- The proposal is generated for a predefined fixture.
- The proposal is not automatically applied to the uploaded repository.
- Synthetic verification does not compile or test the actual uploaded application.
- A successful fixture check does not prove that a real vulnerability has been fixed.

### Main API endpoints

The Phase 3 base path is `/api/v1`.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/health` | Check the remediation service. |
| `POST` | `/api/v1/findings` | Submit a finding for remediation review. |
| `GET` | `/api/v1/findings/{id}` | Retrieve a submitted finding. |
| `POST` | `/api/v1/findings/{id}/proposals` | Generate a proposal for a finding. |
| `GET` | `/api/v1/proposals/{id}` | Retrieve a proposal. |
| `POST` | `/api/v1/proposals/{id}/verify` | Run the configured verification workflow. |
| `GET` | `/api/v1/verifications/{id}` | Retrieve a verification result. |

---

## How the phases fit together

The intended workflow is:

```text
Java source archive
        |
        v
+-----------------------+
| Phase 1: ZeroTrace    |
| Static source scan    |
+-----------------------+
        |
        v
  Findings for review
        |
        +--------------------------+
        |                          |
        v                          v
+--------------------+    +----------------------+
| Phase 2: ZeroWatch |    | Phase 3: ZeroRemediate|
| Runtime-event rules|    | Fix proposal review  |
+--------------------+    +----------------------+
        |                          |
        v                          v
  Security alerts           Proposed code + diff
        |                          |
        +------------+-------------+
                     |
                     v
             Human investigation
```

**Implementation note:** The three phases are available through the common interface, but the current prototype does not automatically transform every Phase 1 finding into live runtime telemetry and then into an applied Phase 3 fix. The demonstrated ZeroWatch and ZeroRemediate flows use synthetic events and fixture-based remediation respectively.

---

## Technology stack

| Area | Technology |
|---|---|
| Backend framework | Java and Spring Boot |
| Build system | Apache Maven |
| Static analysis | Semgrep |
| Frontend | React |
| Frontend development server | Vite |
| API format | HTTP and JSON; multipart upload for source archives |
| Runtime detection | Explicit rule-based event validation and correlation |
| Remediation demo | Deterministic fixture provider |

The React development setup uses a Vite proxy to route API requests to the three backend services. This is a local-development arrangement; deployment requires suitable reverse-proxy configuration or equivalent API routing.

---

## Architecture and ports

The default local development arrangement is:

| Component | Port | Responsibility |
|---|---:|---|
| Phase 1 — ZeroTrace | `8080` | Static analysis API and existing Spring Boot application. |
| Phase 2 — ZeroWatch | `8081` | Runtime-event ingestion and alert API. |
| Phase 3 — ZeroRemediate | `8083` | Finding, proposal, and verification API. |
| React + Vite | `5173` | Development frontend. |

If a service has been configured to use another port, update the Vite proxy accordingly.

### Repository structure

```text
ZeroTraceAI/
├── src/
│   └── main/
│       └── resources/
│           └── static/
│               └── index.html
├── zerowatch/
│   ├── pom.xml
│   └── src/
├── phase3-remediation/
│   ├── pom.xml
│   └── src/
├── zerotrace-react-ui/
│   ├── package.json
│   ├── vite.config.js
│   └── src/
├── rules/
├── benchmarks/
├── docs/
├── pom.xml
├── .gitignore
└── README.md
```

The root Maven project contains Phase 1. ZeroWatch and ZeroRemediate are separate Maven modules. The React interface is maintained in its own directory.

---

## Prerequisites

Install the following before running the complete local demo:

- Git.
- A compatible Java installation for the project.
- Apache Maven.
- Node.js and npm for the React frontend.
- Semgrep, configured as required by the Phase 1 analysis engine.

The project targets Java 17 in its Maven configuration. Use a compatible JDK and confirm the installed runtime works with your local setup.

Check your installations:

```cmd
java -version
mvn -version
node -v
npm -v
```

If Maven is not on your Windows `PATH`, invoke the Maven executable using its installed path.

---

## Installation and local setup

### 1. Clone the repository

```bash
git clone https://github.com/Hackfest-X-Saividya/sentrix-t2-12.git
cd sentrix-t2-12
```

### 2. Start Phase 1 — ZeroTrace

From the repository root:

```bash
mvn spring-boot:run
```

The service is expected to use port `8080` in the local configuration.

### 3. Start Phase 2 — ZeroWatch

Open a second terminal:

```bash
mvn -f zerowatch/pom.xml spring-boot:run
```

Confirm that ZeroWatch is configured to use port `8081`. If its `application.properties` does not specify the port, configure `server.port=8081` for this local arrangement.

### 4. Start Phase 3 — ZeroRemediate

Open a third terminal:

```bash
mvn -f phase3-remediation/pom.xml spring-boot:run
```

Confirm that Phase 3 is configured to use port `8083`.

For the deterministic remediation demo, configure the provider mode as `fixture` if required by the local configuration:

**Windows CMD**

```cmd
set ZEROREMEDIATE_PROVIDER=fixture
mvn -f phase3-remediation/pom.xml spring-boot:run
```

Set the environment variable in the same terminal that starts the service.

### 5. Start the React frontend

Open a fourth terminal:

```bash
cd zerotrace-react-ui
npm install
npm run dev
```

Open the local URL printed by Vite, normally:

`http://127.0.0.1:5173`

The frontend's Vite proxy should route Phase 1, Phase 2, and Phase 3 requests to their respective backend ports.

### 6. Check service health

Run these checks from a terminal:

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8081/api/v1/zerowatch/health
curl http://localhost:8083/api/v1/health
```

Each service must be running on its configured port for the corresponding frontend feature to work.

---

## Using the application

### Source scan

1. Open **Source scan**.
2. Select a ZIP archive containing Java source files.
3. Start the scan.
4. Inspect the scan status and findings register.
5. Review findings before drawing conclusions about exploitability.

Use a flat ZIP containing `.java` files for the current demo if the archive layout is rejected by the upload validation.

### ZeroWatch

1. Open **ZeroWatch**.
2. Use the synthetic event-burst control.
3. Confirm the events were accepted.
4. Refresh the alert list.
5. Inspect the rule, severity, explanation, and supporting event IDs.

If event ingestion fails, check that the Phase 2 backend is running on port `8081` and inspect the HTTP response. A successful alert-list request alone does not prove that event ingestion works.

### ZeroRemediate

1. Open **ZeroRemediate**.
2. Generate the SQL-injection fixture proposal.
3. Inspect the root cause, suggested code, and diff.
4. Run the synthetic verification workflow.
5. Review the verification response.

The current fixture provider supports a bounded demonstration rather than arbitrary remediation of uploaded source code.

---

## API reference

All endpoints listed above are relative to the corresponding backend origin. For example, the ZeroWatch event-ingestion URL in the default local arrangement is:

`http://localhost:8081/api/v1/zerowatch/events`

The API uses JSON for event, finding, proposal, and verification operations. Source upload uses multipart form data.

For the exact request and response fields, consult the request models and controllers in each phase's source tree. The APIs enforce validation, so request bodies must match the implemented schemas.

---

## Testing

Run tests from the relevant project directory.

### Phase 1

```bash
mvn test
```

### Phase 2

```bash
mvn -f zerowatch/pom.xml test
```

### Phase 3

```bash
mvn -f phase3-remediation/pom.xml test
```

### React build

```bash
cd zerotrace-react-ui
npm install
npm run build
```

A successful build checks frontend compilation; it does not prove that the Java APIs are running or that browser-to-backend integration works.

**Known test status:** During development, the Phase 1 full test suite had four analysis-integration tests failing, while the API tests passed 3/3 and project-ingestion tests passed 4/4 at that time. Rerun the tests before release, and update this section with current results rather than assuming all tests pass.

---

## Security and privacy considerations

This project is a development prototype, not a production security appliance.

- Run the demo with sample code and synthetic event data.
- Do not upload secrets, credentials, production source code, or private customer data without appropriate authorization and safeguards.
- Keep local environment variables and credentials out of version control.
- Treat static-analysis findings as review signals.
- Treat runtime alerts as rule matches that require investigation.
- Review remediation proposals before applying them.
- Do not expose development ports or permissive local CORS settings to untrusted networks.
- Configure authentication, authorization, audit logging, retention, and secure deployment controls before any production use.

---

## Current limitations

The current prototype has the following boundaries:

1. **Static analysis:** Results depend on the scanner, configured rules, and source archive. It cannot guarantee detection of all vulnerabilities.
2. **Runtime monitoring:** The demonstrated events are submitted synthetically. Automatic instrumentation of a real application is not implemented in the current demo.
3. **Detection logic:** The demonstrated ZeroWatch alert is rule-based. It is not a general machine-learning anomaly detector.
4. **Remediation provider:** The current Phase 3 demo uses deterministic fixtures, not a live AI provider.
5. **Code application:** Proposed changes are displayed for review and are not automatically written into uploaded source files.
6. **Verification:** The demonstrated checks use synthetic fixtures and do not establish that a real application has been fixed.
7. **Persistence:** Some prototype state may be held in memory and may be lost when a service restarts.
8. **Deployment:** The local Vite proxy and development server are not a production deployment configuration.

---

## Future improvements

Potential next steps include:

- Connect findings to runtime evidence using stable identifiers and an explicit data model.
- Instrument test applications to emit authenticated, structured runtime events.
- Expand rule coverage and add tests for false positives, event ordering, and time-window boundaries.
- Add durable storage and audit trails for scans, events, and alerts.
- Support additional source languages and analysis rules.
- Add a configurable remediation provider with safe timeouts and structured output validation.
- Apply proposed changes only in an isolated workspace, with diffs and explicit human approval.
- Verify proposed changes against actual tests, compilation, and security regression cases.
- Add authentication, authorization, rate limits, and production-grade deployment configuration.

These are future directions, not claims about existing functionality.

---

## Contributing

Contributions are welcome. Before opening a pull request:

1. Describe the problem and the behavior you intend to change.
2. Add or update tests for modified behavior.
3. Run the relevant Maven tests and frontend build.
4. Avoid committing secrets, generated build output, local environment files, or real sensitive data.
5. Document any new endpoint, configuration value, or known limitation.

Keep the implementation status accurate in documentation and demonstrations.

---

## Project repository

**GitHub:** [Hackfest-X-Saividya/sentrix-t2-12](https://github.com/Hackfest-X-Saividya/sentrix-t2-12)

ZeroTrace AI is a hackathon prototype exploring how source analysis, runtime-event correlation, and reviewable remediation proposals can fit into one application security workflow.
