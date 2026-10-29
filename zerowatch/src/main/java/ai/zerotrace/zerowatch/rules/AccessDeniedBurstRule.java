package ai.zerotrace.zerowatch.rules;

import ai.zerotrace.zerowatch.model.EventType;
import ai.zerotrace.zerowatch.model.RuntimeEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AccessDeniedBurstRule implements DetectionRule {
    public static final String ID = "zerowatch.access-denied-burst";
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Override public Optional<Detection> evaluate(RuntimeEvent current, List<RuntimeEvent> events) {
        if (current.eventType() != EventType.ACCESS_DENIED) return Optional.empty();
        String actor = current.attributes().get("actorRef"), resource = current.attributes().get("resource");
        Instant lowerBound = current.timestamp().minus(WINDOW);
        List<RuntimeEvent> sequence = events.stream()
                .filter(e -> e.eventType() == EventType.ACCESS_DENIED)
                .filter(e -> e.serviceName().equals(current.serviceName()))
                .filter(e -> actor.equals(e.attributes().get("actorRef")) && resource.equals(e.attributes().get("resource")))
                .filter(e -> !e.timestamp().isBefore(lowerBound) && !e.timestamp().isAfter(current.timestamp()))
                .sorted(Comparator.comparing(RuntimeEvent::timestamp)).toList();
        if (sequence.size() < 3) return Optional.empty();
        long bucket = Math.floorDiv(current.timestamp().getEpochSecond(), WINDOW.toSeconds());
        return Optional.of(new Detection(ID, "1.0.0", ID + ":" + current.serviceName() + ":" + actor + ":" + resource + ":" + bucket,
                WINDOW, sequence.stream().map(RuntimeEvent::eventId).toList(), "MEDIUM",
                "Three or more access denials for the same pseudonymous actor and resource occurred within five minutes.",
                List.of("Observed " + sequence.size() + " ACCESS_DENIED events in a five-minute window.", "Actor and resource values are allowlisted pseudonymous identifiers.")));
    }
}
