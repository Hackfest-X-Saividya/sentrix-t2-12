package ai.zerotrace.zeroremediate.service;

import ai.zerotrace.zeroremediate.model.ProposalDraft;
import org.springframework.stereotype.Component;
import java.util.regex.*;

@Component
public class ProposalValidator {
    public void validate(ProposalDraft p,String path) {
        if(p==null || p.rootCause()==null || p.remediation()==null || p.proposedSnippet()==null || p.unifiedDiff()==null || p.securityRationale()==null || p.assumptions()==null || p.suggestedRegressionTest()==null) throw new RemediationException(502,"Malformed provider response");
        if(p.rootCause().length()>1200 || p.remediation().length()>1200 || p.proposedSnippet().length()>4096 || p.unifiedDiff().length()>8192 || p.securityRationale().length()>1200 || p.suggestedRegressionTest().length()>1200 || p.assumptions().size()>8 || p.assumptions().stream().anyMatch(a->a==null || a.length()>512)) throw new RemediationException(502,"Provider response exceeds limits");
        Matcher oldPath=Pattern.compile("(?m)^--- a/(.+)$").matcher(p.unifiedDiff());
        Matcher newPath=Pattern.compile("(?m)^\\+\\+\\+ b/(.+)$").matcher(p.unifiedDiff());
        int oldCount=0,newCount=0;
        while(oldPath.find()){oldCount++; if(!oldPath.group(1).equals(path)) throw new RemediationException(502,"Patch targets a path other than the finding path");}
        while(newPath.find()){newCount++; if(!newPath.group(1).equals(path)) throw new RemediationException(502,"Patch targets a path other than the finding path");}
        if(oldCount!=1 || newCount!=1 || p.unifiedDiff().contains("\0") || p.unifiedDiff().contains("\r") || p.unifiedDiff().contains("../") || p.unifiedDiff().contains("\\\\")) throw new RemediationException(502,"Unsafe or malformed patch");
    }
}
