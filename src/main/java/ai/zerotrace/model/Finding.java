package ai.zerotrace.model;

import java.util.List;

public record Finding(String id, String ruleId, String category, String status, String file,
                      Integer startLine, Integer endLine, Location source, Location sink,
                      List<TraceStep> trace, List<String> protections, String severity,
                      String confidence, String explanation, String nextStep,
                      String engine, String ruleVersion) {
    public record Location(String file, Integer line, String code) {}
    public record TraceStep(String file, Integer line, String code) {}
}
