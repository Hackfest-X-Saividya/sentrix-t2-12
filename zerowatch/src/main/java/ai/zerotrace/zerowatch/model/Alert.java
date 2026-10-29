package ai.zerotrace.zerowatch.model;

import java.time.Instant;
import java.util.List;

public record Alert(String alertId, String ruleId, String ruleVersion, List<String> eventIds,
                    Instant detectedAt, String severity, String explanation, List<String> evidence,
                    String phase1FindingId, String status) {
    public Alert { eventIds = List.copyOf(eventIds); evidence = List.copyOf(evidence); }
}
