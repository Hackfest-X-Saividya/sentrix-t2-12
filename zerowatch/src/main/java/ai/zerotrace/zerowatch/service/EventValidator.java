package ai.zerotrace.zerowatch.service;

import ai.zerotrace.zerowatch.model.EventType;
import ai.zerotrace.zerowatch.model.RuntimeEventRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class EventValidator {
    private static final Set<String> RESOURCES = Set.of("admin-console", "customer-record", "billing-settings", "health-check");
    private static final Set<String> OPERATIONS = Set.of("RAW_SQL_EXECUTION", "PARAMETERIZED_SQL_EXECUTION", "FILE_READ", "PROCESS_START");
    private final Clock clock;
    public EventValidator(Clock clock) { this.clock = clock; }

    public void validate(RuntimeEventRequest event) {
        if (!"1.0".equals(event.schemaVersion())) throw new InvalidEventException("schemaVersion must be 1.0.");
        Instant now = clock.instant();
        if (event.timestamp().isBefore(now.minus(Duration.ofHours(24))) || event.timestamp().isAfter(now.plus(Duration.ofMinutes(5))))
            throw new InvalidEventException("timestamp must be within the last 24 hours and no more than 5 minutes in the future.");
        if (event.eventId() != null) {
            try {
                if (!UUID.fromString(event.eventId()).toString().equalsIgnoreCase(event.eventId())) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new InvalidEventException("eventId must be a canonical UUID when supplied."); }
        }
        if (event.findingId() != null && !event.findingId().matches("[A-Za-z0-9._:-]{1,128}"))
            throw new InvalidEventException("findingId contains unsupported characters.");
        if (!safeCorrelationId(event.requestId()) || !safeCorrelationId(event.traceId()))
            throw new InvalidEventException("requestId and traceId may contain only letters, digits, dot, underscore, colon, and hyphen.");
        Map<String, String> a = event.attributes();
        switch (event.eventType()) {
            case APP_STARTED -> requireKeys(a, Set.of());
            case ACCESS_DENIED, ACCESS_GRANTED -> {
                requireKeys(a, Set.of("actorRef", "resource"));
                validateActor(a.get("actorRef")); validateResource(a.get("resource"));
            }
            case SENSITIVE_OPERATION -> {
                requireKeys(a, Set.of("operation", "outcome"));
                if (!OPERATIONS.contains(a.get("operation"))) throw new InvalidEventException("Unsupported sensitive operation.");
                if (!Set.of("ALLOWED", "DENIED").contains(a.get("outcome"))) throw new InvalidEventException("outcome must be ALLOWED or DENIED.");
            }
        }
    }
    private boolean safeCorrelationId(String id) { return id == null || id.matches("[A-Za-z0-9._:-]{1,64}"); }
    private void requireKeys(Map<String, String> attributes, Set<String> expected) {
        if (attributes == null || !attributes.keySet().equals(expected))
            throw new InvalidEventException("Event attributes do not match the allowlist for this event type.");
    }
    private void validateActor(String actor) {
        if (actor == null || !actor.matches("actor-[a-fA-F0-9]{8,32}"))
            throw new InvalidEventException("actorRef must be a pseudonymous actor-* hexadecimal identifier.");
    }
    private void validateResource(String resource) {
        if (!RESOURCES.contains(resource)) throw new InvalidEventException("resource is not in the configured allowlist.");
    }
}
