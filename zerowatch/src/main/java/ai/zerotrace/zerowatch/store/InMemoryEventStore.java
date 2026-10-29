package ai.zerotrace.zerowatch.store;

import ai.zerotrace.zerowatch.model.RuntimeEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryEventStore implements EventStore {
    private static final int MAX_EVENTS = 10_000;
    private final ConcurrentHashMap<String, RuntimeEvent> events = new ConcurrentHashMap<>();
    @Override public RuntimeEvent save(RuntimeEvent event) {
        if (events.size() >= MAX_EVENTS) throw new StoreCapacityException();
        if (events.putIfAbsent(event.eventId(), event) != null) throw new DuplicateEventException();
        return event;
    }
    @Override public Optional<RuntimeEvent> get(String id) { return Optional.ofNullable(events.get(id)); }
    @Override public List<RuntimeEvent> latest(int limit) {
        return events.values().stream().sorted(Comparator.comparing(RuntimeEvent::ingestedAt).reversed()).limit(limit).toList();
    }
    @Override public List<RuntimeEvent> between(Instant from, Instant through) {
        return events.values().stream().filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(through)).toList();
    }
    @Override public void deleteIngestedBefore(Instant cutoff) { events.values().removeIf(e -> e.ingestedAt().isBefore(cutoff)); }
    @Override public int size() { return events.size(); }
}
