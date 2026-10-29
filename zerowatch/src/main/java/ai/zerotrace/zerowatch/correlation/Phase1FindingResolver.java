package ai.zerotrace.zerowatch.correlation;

import java.util.Optional;

/** Optional port: return metadata only when an exact, reliable Phase 1 finding ID is known. */
public interface Phase1FindingResolver {
    Optional<Phase1FindingReference> resolve(String findingId);
}
