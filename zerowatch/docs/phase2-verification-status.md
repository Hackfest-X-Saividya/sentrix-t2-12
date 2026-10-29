# ZeroWatch Phase 2 Verification Status

**Status:** 16-test Maven baseline passed; concurrency tests pending execution; broader verification pending  
**Recorded:** 2026-10-28
**Phase 1 status:** Implemented Prototype — Verification Pending (unchanged)
**Phase 3:** Not started.

## Implemented in the isolated module

- Independent Spring Boot Maven module at `zerowatch/`.
- Versioned structured event model, event validation, payload cap, allowlisted metadata, safe logging, bounded query APIs, in-memory event/alert stores, and 24-hour pruning.
- Deterministic denied-access burst and unexpected allowed raw-SQL operation rules with evidence event IDs and documented suppression policies.
- Optional exact-ID Phase 1 resolver port; default resolver correlates nothing.
- Synthetic event producer and JSON fixtures.
- MockMvc acceptance tests for API success, invalid requests, rules, evidence, deduplication, correlation boundary, and sensitive data handling.

## Successful Maven/Surefire run

The latest successful Surefire report files are present in `zerowatch/target/surefire-reports/`. Exactly one XML suite was found: `TEST-ai.zerotrace.zerowatch.ZeroWatchApiTest.xml`.

Command reported for the clean acceptance run:

```powershell
mvn -f zerowatch/pom.xml clean test
```

Build result reported by the user:

```text
BUILD SUCCESS
```

Surefire text report (`ai.zerotrace.zerowatch.ZeroWatchApiTest.txt`):

```text
Test set: ai.zerotrace.zerowatch.ZeroWatchApiTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.609 s -- in ai.zerotrace.zerowatch.ZeroWatchApiTest
```

The XML report independently records `tests="16"`, `failures="0"`, `errors="0"`, `skipped="0"`, and `time="8.609"`. Its test process reports `java.specification.version=27`. Report files were timestamped 2026-10-28 18:52:24 in the workspace's local time. The XML includes the evidence-traceability, duplicate-ID, inclusive-boundary, and just-outside-window tests. Test logs include `zerowatch.access-denied-burst`, `MEDIUM`, and `evidenceEvents=3`.

All 16 test cases are listed in the XML report, including health, valid event retrieval, missing fields, malformed timestamp/unknown type, oversized payload, denied-burst detection, evidence traceability, normal-event suppression, raw-SQL rule, deduplication, duplicate event ID, both five-minute boundary cases, finding-reference preservation, sensitive-field/severity rejection, and bounded limits/unknown IDs.

The `BUILD SUCCESS` result and command are reported by the user; Surefire counts, test duration, and included methods were checked directly against the report files. Three concurrency tests have since been added, so this 16-test report is the baseline and does not cover those additions.

## Earlier environment attempt

Before the successful Maven run became available, an attempt in the restricted shell was blocked because `mvn` was not found. That earlier failure is superseded by the Surefire evidence above.

## Evidence-traceability regression added after the baseline run

Added `alertEvidenceIdsMatchSubmittedAndStoredEvents` to `ZeroWatchApiTest`. It submits the fixture sequence with an isolated actor/service, captures the returned event IDs, compares the alert evidence ID set exactly against those IDs, retrieves every referenced event, and compares its stored request fields to the submitted event. It also checks the alert rule and that only one alert was created.

The required clean-suite command was attempted after this test was added:

```powershell
mvn -f zerowatch/pom.xml clean test
```

Actual result:

```text
mvn:
Line |
   2 |  mvn -f zerowatch/pom.xml clean test
     |  ~~~
     | The term 'mvn' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
```

Maven did not start, so `clean` did not run and the new test was not executed by that attempt. A later successful Surefire run included this regression; the current 16-test report is recorded above.

## Duplicate-event-ID regression added after the 13-test baseline

Inspected the existing API contract: `zerowatch/README.md` documents HTTP 409 for duplicate event IDs; `ApiExceptionHandler` maps `DuplicateEventException` to 409 with `duplicate_event`; `InMemoryEventStore.save` rejects an existing UUID with `putIfAbsent`.

Added `duplicateEventIdIsRejectedWithoutDuplicatingStorageOrAlertEvidence` to `ZeroWatchApiTest`. It posts the same explicit UUID and identical payload twice, asserts the duplicate receives HTTP 409, submits a second distinct denial and asserts no alert yet, submits a third distinct denial, compares the alert evidence set to exactly the three unique UUIDs, and verifies the event list contains each unique UUID once.

The required command was attempted after adding this test:

```powershell
mvn -f zerowatch/pom.xml clean test
```

Actual output:

```text
mvn:
Line |
   2 |  mvn -f zerowatch/pom.xml clean test
     |  ~~~
     | The term 'mvn' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
```

That attempt did not start Maven, so `clean` did not run and the duplicate-ID test was not executed by that attempt. A later successful 16-test run is recorded above; its Surefire XML includes the duplicate-ID method. The report is current through the boundary tests, but stale with respect to the concurrency tests added below.

## Five-minute burst-window boundary tests added after the 14-test baseline

Inspected `AccessDeniedBurstRule`, `EventService`, and `InMemoryEventStore` before selecting assertions. The rule requires at least three `ACCESS_DENIED` events with the same service, actor, and resource. It selects the inclusive event-time interval from `current.timestamp - 5 minutes` through `current.timestamp`, then sorts matching events by event timestamp. `EventService` queries with the same event-time interval. Ingestion time is not used for burst qualification; it is used for retention and latest-event ordering.

Added two deterministic API tests to `ZeroWatchApiTest`:

- `accessDeniedBurstTriggersAtInclusiveFiveMinuteBoundary` submits three events where the oldest is exactly five minutes before the third and expects one burst alert. This asserts the implementation's inclusive lower bound.
- `accessDeniedBurstDoesNotTriggerJustOutsideFiveMinuteWindow` submits a third event one millisecond beyond that five-minute span and expects no alert, because only two events remain in the qualifying window.

Both tests use explicit timestamps before the fixed test clock, unique service/actor values, and no sleeps.

The required clean-suite command was attempted after adding the boundary tests:

```powershell
mvn -f zerowatch/pom.xml clean test
```

Actual output:

```text
mvn:
Line |
   2 |  mvn -f zerowatch/pom.xml clean test
     |  ~~~
     | The term 'mvn' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
```

The initial attempt in this shell did not start Maven. A later successful 16-test run is recorded above, and its Surefire XML includes both boundary method names. Those results verify the inclusive and just-outside scenarios in the test suite; the report is now the baseline for the concurrency tests added below.

## Concurrency audit and tests added after the 16-test baseline

Implementation guarantees inspected before adding tests:

- `EventService.ingest` is `synchronized` on the singleton service instance. In one running application instance, validation, retention pruning, event insertion, rule evaluation, correlation, and alert storage are serialized for API ingestion requests.
- `InMemoryEventStore` and `InMemoryAlertStore` use `ConcurrentHashMap`. Event IDs use `putIfAbsent`, which atomically rejects duplicate keys. The event-count capacity check is a separate `size()` read, but API ingestion reaches it under the service monitor.
- `AccessDeniedBurstRule` is stateless. It evaluates a supplied event snapshot using event timestamps. `RuleEvaluator` keeps its dedup keys in a `ConcurrentHashMap` and uses `putIfAbsent`/`replace`; those map operations are atomic individually, but map deduplication and alert persistence are not one cross-store transaction.
- `ZeroWatchController` has no synchronization of its own and delegates to `EventService`. These properties describe a single in-memory application instance; they do not define cross-process or multi-instance coordination guarantees.

Added three tests to `ZeroWatchApiTest` with a bounded executor, start gate, timed futures, and executor shutdown in `finally`:

- `concurrentUniqueEventsAreAllRetrievable` concurrently submits 24 distinct IDs and retrieves each accepted event.
- `concurrentDuplicateEventIdHasOneWinnerAndOneStoredRecord` concurrently submits the same payload/UUID eight times; it checks one HTTP 201, seven documented HTTP 409 responses, and one stored record.
- `concurrentDeniedBurstProducesOneAlertWithValidUniqueStoredEvidence` concurrently submits 12 qualifying events; it checks one returned/stored alert and that evidence IDs are unique, belong to the batch, and retrieve as stored matching events. Assertions do not depend on which request acquires the service monitor first.

A compile-only check of the updated `ZeroWatchApiTest.java` passed with JDK 24 `javac --release 17`, using the dependency classpath recorded by the existing Surefire report. Its temporary output directory was removed. This confirms the test source compiles against the cached dependencies, but it did not execute the test methods.

The clean-suite command was attempted after adding these tests:

```powershell
mvn -f zerowatch/pom.xml clean test
```

Actual output:

```text
mvn:
Line |
   2 |  mvn -f zerowatch/pom.xml clean test
     |  ~~~
     | The term 'mvn' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
```

Maven did not start, so `clean` did not run and none of the concurrency tests has been executed. The Surefire files remain the 16-test baseline; the current source contains 19 test methods. No concurrency pass/fail result is claimed.

## Environment details

Development shell JDK check before the Maven run:

```powershell
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -version
```

Output:

```text
java version "24.0.2" 2025-07-15
Java(TM) SE Runtime Environment (build 24.0.2+12-54)
Java HotSpot(TM) 64-Bit Server VM (build 24.0.2+12-54, mixed mode, sharing)
```

The JDK 24 check below was performed in the development shell before the successful test run. The Surefire report shows that the successful test process itself used Java specification version 27.

XML check command:

```powershell
python -c "import xml.etree.ElementTree as ET; ET.parse('pom.xml'); ET.parse('zerowatch/pom.xml'); print('PASS: root and ZeroWatch pom.xml files are well-formed XML')"
```

Output: `PASS: root and ZeroWatch pom.xml files are well-formed XML`.

`git diff --check` produced no diagnostics. The current SHA-256 of the preserved Phase 1 verification record is `293ACC07C2673513EA48E877ADDEFBEA1DE374EA5F0540254B63BFD44DE693D1` (`docs/verification-status.md`).

## JDK-only rule smoke check

To exercise the pure deterministic rule classes without Spring/Maven, a PowerShell script compiles the production rule/model classes with a temporary `@Component` annotation stub, then runs assertions for the suspicious sequence, supporting event IDs, deduplication, normal access, parameterized SQL, and the raw-SQL rule.

Command:

```powershell
./zerowatch/scripts/verify-rules.ps1
```

Output:

```text
PASS: denied burst detection, 3-event evidence, deduplication, normal-event suppression, parameterized SQL suppression, raw-SQL detection
```

This focused JVM check is separate from the full Spring Boot build and MockMvc/JUnit suite, which are recorded in the Surefire section above.

## Git isolation record

Initial commit: `2d8eff66e15708e2d0cefb9c945716327ccd4cf5` on `master`. The pre-existing Phase 1 files were untracked at the time of capture. Creating `feature/phase-2-zerowatch` failed because Git could not create a ref directory under `.git`. The fallback module was used; root Maven configuration, Phase 1 source, and `docs/verification-status.md` were not edited. See `initial-git-state.md` for exact status and command output.

The 16-test Maven suite passed and its Surefire XML includes the evidence-traceability, duplicate-ID, and burst-window boundary tests. The three concurrency tests remain pending execution. Phase 2 remains **In Development — broader verification pending** because concurrency behavior after the latest test changes, the exact-ID positive correlation path, additional operational/security review, and reproducibility from a clean environment have not been demonstrated. Phase 1 remains **Implemented Prototype — Verification Pending**; Phase 2 results do not satisfy or change Phase 1 acceptance criteria.
