package ai.zerotrace.zerowatch.correlation;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoPhase1Findings implements Phase1FindingResolver {
    @Override public Optional<Phase1FindingReference> resolve(String findingId) { return Optional.empty(); }
}
