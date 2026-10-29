package ai.zerotrace.zeroremediate.provider;

import ai.zerotrace.zeroremediate.model.ProposalDraft;
import ai.zerotrace.zeroremediate.model.SecurityFinding;

public interface RemediationProvider {
    String mode();
    ProposalDraft propose(SecurityFinding finding);
}
