package ai.zerotrace.zeroremediate.provider;

import ai.zerotrace.zeroremediate.model.SecurityFinding;
import ai.zerotrace.zeroremediate.service.RemediationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="zeroremediate.provider",havingValue="none")
public class UnavailableRemediationProvider implements RemediationProvider {
    public String mode(){return "UNAVAILABLE";}
    public ai.zerotrace.zeroremediate.model.ProposalDraft propose(SecurityFinding f){throw new RemediationException(503,"No remediation provider is configured");}
}
