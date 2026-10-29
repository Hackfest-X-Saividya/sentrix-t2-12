package ai.zerotrace.zerowatch.model;

import java.time.Instant;
import java.util.Map;

public record RuntimeEvent(String eventId, String schemaVersion, Instant timestamp, Instant ingestedAt,
                           String serviceName, EventType eventType, String requestId, String traceId,
                           String findingId, Map<String, String> attributes) {
    public RuntimeEvent { attributes = Map.copyOf(attributes); }
}
