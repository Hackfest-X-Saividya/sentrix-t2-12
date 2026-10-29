package ai.zerotrace.zeroremediate.model;

import java.time.Instant;
import java.util.List;

public record VerificationReport(String verificationId, String proposalId, String findingId,
                                 Outcome outcome, String claim, String scope,
                                 List<VerificationCheck> checks, List<String> limitations,
                                 Instant completedAt) {
    public enum Outcome { VERIFIED, FAILED, INCONCLUSIVE }
    public VerificationReport {
        checks = List.copyOf(checks);
        limitations = List.copyOf(limitations);
    }
}
