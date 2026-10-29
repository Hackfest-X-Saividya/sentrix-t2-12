package ai.zerotrace.zeroremediate.model;

import java.time.Instant;
import java.util.List;

public record RemediationProposal(String proposalId, String findingId, String providerMode,
                                  String rootCause, String remediation, String proposedSnippet,
                                  String unifiedDiff, String securityRationale, List<String> assumptions,
                                  String suggestedRegressionTest, Instant createdAt) {
    public RemediationProposal { assumptions = List.copyOf(assumptions); }
}
