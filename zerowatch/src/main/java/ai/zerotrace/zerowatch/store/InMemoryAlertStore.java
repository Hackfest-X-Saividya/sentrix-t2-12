package ai.zerotrace.zerowatch.store;

import ai.zerotrace.zerowatch.model.Alert;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAlertStore implements AlertStore {
    private static final int MAX_ALERTS = 5_000;
    private final ConcurrentHashMap<String, Alert> alerts = new ConcurrentHashMap<>();
    @Override public Alert save(Alert alert) {
        if (alerts.size() >= MAX_ALERTS) throw new StoreCapacityException();
        alerts.put(alert.alertId(), alert); return alert;
    }
    @Override public Optional<Alert> get(String id) { return Optional.ofNullable(alerts.get(id)); }
    @Override public List<Alert> latest(int limit) {
        return alerts.values().stream().sorted(Comparator.comparing(Alert::detectedAt).reversed()).limit(limit).toList();
    }
    @Override public void deleteDetectedBefore(Instant cutoff) { alerts.values().removeIf(a -> a.detectedAt().isBefore(cutoff)); }
}
