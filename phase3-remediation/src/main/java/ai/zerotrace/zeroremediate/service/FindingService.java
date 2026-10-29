package ai.zerotrace.zeroremediate.service;

import ai.zerotrace.zeroremediate.model.*;
import ai.zerotrace.zeroremediate.provider.RemediationProvider;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class FindingService {
    private final FindingStore store; private final RemediationProvider provider; private final ProposalValidator validator; private final VerificationRunner verifier;
    public FindingService(FindingStore s,RemediationProvider p,ProposalValidator v,VerificationRunner r){store=s;provider=p;validator=v;verifier=r;}
    public SecurityFinding submit(FindingSubmission in){
        if(!in.ruleId().equals("zerotrace.java.sql-injection") || !in.category().equalsIgnoreCase("sql-injection")) throw new RemediationException(422,"MVP supports only zerotrace.java.sql-injection findings");
        return store.put(in);
    }
    public RemediationProposal propose(String id){
        SecurityFinding f=store.finding(id); ProposalDraft d;
        try{d=provider.propose(f);}catch(RemediationException e){throw e;}catch(RuntimeException e){throw new RemediationException(502,"Provider returned an invalid or unsupported response");}
        validator.validate(d,f.filePath());
        RemediationProposal p=new RemediationProposal(UUID.randomUUID().toString(),f.findingId(),provider.mode(),d.rootCause(),d.remediation(),d.proposedSnippet(),d.unifiedDiff(),d.securityRationale(),d.assumptions(),d.suggestedRegressionTest(),Instant.now());store.save(p);return p;
    }
    public VerificationReport verify(String id){return verifier.verify(store.proposal(id));}
    public String providerMode(){return provider.mode();}
    public FindingStore store(){return store;}
}
