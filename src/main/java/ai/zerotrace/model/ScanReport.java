package ai.zerotrace.model;

import java.time.Instant;
import java.util.List;

public record ScanReport(String schemaVersion, String scanId, Instant completedAt, String status,
                         int filesAnalyzed, int findingsCount, List<Finding> findings,
                         List<String> limitations, String engine) {}
