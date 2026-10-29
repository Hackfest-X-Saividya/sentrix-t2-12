package ai.zerotrace.zerowatch.rules;

import java.time.Duration;
import java.util.List;

public record Detection(String ruleId, String ruleVersion, String deduplicationKey, Duration deduplicationWindow,
                        List<String> eventIds, String severity, String explanation, List<String> evidence) {
    public Detection { eventIds = List.copyOf(eventIds); evidence = List.copyOf(evidence); }
}
