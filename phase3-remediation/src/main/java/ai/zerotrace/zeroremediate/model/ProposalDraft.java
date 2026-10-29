package ai.zerotrace.zeroremediate.model;

import java.util.List;

public record ProposalDraft(String rootCause, String remediation, String proposedSnippet,
                            String unifiedDiff, String securityRationale,
                            List<String> assumptions, String suggestedRegressionTest) {
    public ProposalDraft { assumptions = List.copyOf(assumptions); }
}
