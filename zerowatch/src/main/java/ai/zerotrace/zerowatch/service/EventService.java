package ai.zerotrace.zerowatch.service;

import ai.zerotrace.zerowatch.correlation.Phase1FindingResolver;
import ai.zerotrace.zerowatch.model.Alert;
import ai.zerotrace.zerowatch.model.EventIngestionResponse;
import ai.zerotrace.zerowatch.model.RuntimeEvent;
import ai.zerotrace.zerowatch.model.RuntimeEventRequest;
import ai.zerotrace.zerowatch.rules.Detection;
import ai.zerotrace.zerowatch.rules.RuleEvaluator;
import ai.zerotrace.zerowatch.store.AlertStore;
import ai.zerotrace.zerowatch.store.EventStore;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Duration RULE_LOOKBACK = Duration.ofMinutes(5);
    private final EventStore events;
    private final AlertStore alerts;
    private final EventValidator validator;
    private final RuleEvaluator evaluator;
    private final Phase1FindingResolver findingResolver;
    private final Clock clock;

    public EventService(EventStore events, AlertStore alerts, EventValidator validator, RuleEvaluator evaluator,
                        Phase1FindingResolver findingResolver, Clock clock) {
        this.events = events; this.alerts = alerts; this.validator = validator;
        this.evaluator = evaluator; this.findingResolver = findingResolver; this.clock = clock;
    }

    public synchronized EventIngestionResponse ingest(RuntimeEventRequest request) {
        validator.validate(request);
        var now = clock.instant();
        events.deleteIngestedBefore(now.minus(RETENTION));
        alerts.deleteDetectedBefore(now.minus(RETENTION));
        var event = new RuntimeEvent(request.eventId() == null ? UUID.randomUUID().toString() : request.eventId(),
                request.schemaVersion(), request.timestamp(), now, request.serviceName(), request.eventType(),
                request.requestId(), request.traceId(), request.findingId(), request.attributes());
        events.save(event);
        var recent = events.between(event.timestamp().minus(RULE_LOOKBACK), event.timestamp());
        List<Alert> created = new ArrayList<>();
        for (Detection detection : evaluator.evaluate(event, recent)) {
            String correlatedFinding = detection.eventIds().stream().map(events::get).flatMap(java.util.Optional::stream)
                    .map(RuntimeEvent::findingId).filter(java.util.Objects::nonNull)
                    .map(findingResolver::resolve).flatMap(java.util.Optional::stream)
                    .map(ref -> ref.findingId()).findFirst().orElse(null);
            Alert alert = alerts.save(new Alert(UUID.randomUUID().toString(), detection.ruleId(), detection.ruleVersion(),
                    detection.eventIds(), now, detection.severity(), detection.explanation(), detection.evidence(),
                    correlatedFinding, "OPEN"));
            created.add(alert);
            log.warn("ZeroWatch alert created id={} rule={} severity={} evidenceEvents={}",
                    alert.alertId(), alert.ruleId(), alert.severity(), alert.eventIds().size());
        }
        log.info("ZeroWatch event accepted id={} type={} service={}", event.eventId(), event.eventType(), event.serviceName());
        return new EventIngestionResponse(event, created);
    }
    public List<RuntimeEvent> latestEvents(int limit) { return events.latest(validateLimit(limit)); }
    public java.util.Optional<RuntimeEvent> event(String id) { return events.get(id); }
    public List<Alert> latestAlerts(int limit) { return alerts.latest(validateLimit(limit)); }
    public java.util.Optional<Alert> alert(String id) { return alerts.get(id); }
    private int validateLimit(int limit) {
        if (limit < 1 || limit > 500) throw new InvalidEventException("limit must be between 1 and 500.");
        return limit;
    }
}
