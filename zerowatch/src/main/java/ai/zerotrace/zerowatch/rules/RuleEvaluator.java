package ai.zerotrace.zerowatch.rules;

import ai.zerotrace.zerowatch.model.RuntimeEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RuleEvaluator {
    private final List<DetectionRule> rules;
    private final ConcurrentHashMap<String, Instant> lastAlertByKey = new ConcurrentHashMap<>();
    public RuleEvaluator(List<DetectionRule> rules) { this.rules = List.copyOf(rules); }
    public List<Detection> evaluate(RuntimeEvent current, List<RuntimeEvent> events) {
        List<Detection> results = new ArrayList<>();
        for (DetectionRule rule : rules) rule.evaluate(current, events).ifPresent(detection -> {
            Instant previous = lastAlertByKey.putIfAbsent(detection.deduplicationKey(), current.timestamp());
            if (previous == null) results.add(detection);
            else if (!current.timestamp().isBefore(previous.plus(detection.deduplicationWindow()))
                    && lastAlertByKey.replace(detection.deduplicationKey(), previous, current.timestamp())) results.add(detection);
        });
        Instant cutoff = current.timestamp().minus(DurationHolder.MAX_DEDUP_AGE);
        lastAlertByKey.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        return List.copyOf(results);
    }
    private static final class DurationHolder { private static final java.time.Duration MAX_DEDUP_AGE = java.time.Duration.ofHours(1); }
}
