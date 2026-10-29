package ai.zerotrace.zerowatch.rules;

import ai.zerotrace.zerowatch.model.EventType;
import ai.zerotrace.zerowatch.model.RuntimeEvent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RawSqlOperationRule implements DetectionRule {
    public static final String ID = "zerowatch.unexpected-raw-sql-operation";
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(5);

    @Override public Optional<Detection> evaluate(RuntimeEvent current, List<RuntimeEvent> events) {
        if (current.eventType() != EventType.SENSITIVE_OPERATION
                || !"RAW_SQL_EXECUTION".equals(current.attributes().get("operation"))
                || !"ALLOWED".equals(current.attributes().get("outcome"))) return Optional.empty();
        long bucket = Math.floorDiv(current.timestamp().getEpochSecond(), DEDUP_WINDOW.toSeconds());
        return Optional.of(new Detection(ID, "1.0.0", ID + ":" + current.serviceName() + ":" + bucket,
                DEDUP_WINDOW, List.of(current.eventId()), "HIGH",
                "The demo application reported an allowed raw SQL execution operation.",
                List.of("Event type is SENSITIVE_OPERATION.", "Operation RAW_SQL_EXECUTION was reported with outcome ALLOWED.",
                        "This runtime signal is suspicious context, not proof of SQL injection or exploitation.")));
    }
}
