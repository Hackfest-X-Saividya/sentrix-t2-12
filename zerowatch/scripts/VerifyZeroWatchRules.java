import ai.zerotrace.zerowatch.model.EventType;
import ai.zerotrace.zerowatch.model.RuntimeEvent;
import ai.zerotrace.zerowatch.rules.AccessDeniedBurstRule;
import ai.zerotrace.zerowatch.rules.RawSqlOperationRule;
import ai.zerotrace.zerowatch.rules.RuleEvaluator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VerifyZeroWatchRules {
    private static RuntimeEvent event(String id, EventType type, Instant at, Map<String, String> attrs) {
        return new RuntimeEvent(id, "1.0", at, at, "rule-harness", type, null, null, null, attrs);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var rules = new RuleEvaluator(List.of(new AccessDeniedBurstRule(), new RawSqlOperationRule()));
        Instant start = Instant.parse("2026-10-28T10:00:00Z");
        List<RuntimeEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var current = event("deny-" + i, EventType.ACCESS_DENIED, start.plusSeconds(i * 20L),
                    Map.of("actorRef", "actor-deadbeef1234", "resource", "admin-console"));
            events.add(current);
            var found = rules.evaluate(current, List.copyOf(events));
            check(found.size() == (i == 2 ? 1 : 0), "denied burst alert/dedup at index " + i);
            if (i == 2) check(found.get(0).eventIds().size() == 3, "preserves three evidence IDs");
        }
        var normal = event("normal", EventType.ACCESS_GRANTED, start,
                Map.of("actorRef", "actor-cafebabe1234", "resource", "health-check"));
        check(rules.evaluate(normal, List.of(normal)).isEmpty(), "normal access should not alert");
        var parameterized = event("parameterized", EventType.SENSITIVE_OPERATION, start,
                Map.of("operation", "PARAMETERIZED_SQL_EXECUTION", "outcome", "ALLOWED"));
        check(rules.evaluate(parameterized, List.of(parameterized)).isEmpty(), "parameterized SQL should not trigger raw SQL rule");
        var raw = event("raw", EventType.SENSITIVE_OPERATION, start,
                Map.of("operation", "RAW_SQL_EXECUTION", "outcome", "ALLOWED"));
        var rawAlerts = rules.evaluate(raw, List.of(raw));
        check(rawAlerts.size() == 1 && rawAlerts.get(0).eventIds().equals(List.of("raw")), "raw SQL rule includes evidence");
        System.out.println("PASS: denied burst detection, 3-event evidence, deduplication, normal-event suppression, parameterized SQL suppression, raw-SQL detection");
    }
}
