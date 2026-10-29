package ai.zerotrace.zerowatch.model;

import java.util.List;

public record EventIngestionResponse(RuntimeEvent event, List<Alert> alerts) {
    public EventIngestionResponse { alerts = List.copyOf(alerts); }
}
