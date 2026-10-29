package ai.zerotrace.zerowatch.store;

import ai.zerotrace.zerowatch.model.Alert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertStore {
    Alert save(Alert alert);
    Optional<Alert> get(String alertId);
    List<Alert> latest(int limit);
    void deleteDetectedBefore(Instant cutoff);
}
