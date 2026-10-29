package ai.zerotrace.zerowatch.store;

import ai.zerotrace.zerowatch.model.RuntimeEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventStore {
    RuntimeEvent save(RuntimeEvent event);
    Optional<RuntimeEvent> get(String eventId);
    List<RuntimeEvent> latest(int limit);
    List<RuntimeEvent> between(Instant from, Instant through);
    void deleteIngestedBefore(Instant cutoff);
    int size();
}
