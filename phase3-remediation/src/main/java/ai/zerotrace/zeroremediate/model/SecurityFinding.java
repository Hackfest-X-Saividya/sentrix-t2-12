package ai.zerotrace.zeroremediate.model;

import java.time.Instant;
import java.util.List;

public record SecurityFinding(String findingId, String ruleId, String category, FindingSeverity severity,
                              String filePath, Integer lineStart, Integer lineEnd, List<String> evidence,
                              String explanation, String codeContext, Instant receivedAt) {
    public SecurityFinding { evidence = List.copyOf(evidence); }
}
