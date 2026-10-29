package ai.zerotrace.zerowatch.rules;

import ai.zerotrace.zerowatch.model.RuntimeEvent;
import java.util.List;
import java.util.Optional;

public interface DetectionRule {
    Optional<Detection> evaluate(RuntimeEvent current, List<RuntimeEvent> events);
}
