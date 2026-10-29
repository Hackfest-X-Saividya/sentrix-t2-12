package ai.zerotrace.zeroremediate.service;

import ai.zerotrace.zeroremediate.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FindingStore {
    private static final int LIMIT = 1000;
    private final Map<String, SecurityFinding> findings = new ConcurrentHashMap<>();
    private final Map<String, RemediationProposal> proposals = new ConcurrentHashMap<>();
    private final Map<String, VerificationReport> reports = new ConcurrentHashMap<>();
    public synchronized SecurityFinding put(FindingSubmission in) {
        if (findings.size() >= LIMIT && !findings.containsKey(in.findingId())) throw new RemediationException(507,"In-memory audit capacity reached");
        FindingSeverity severity;
        try { severity = FindingSeverity.valueOf(in.severity().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new RemediationException(400,"Unsupported severity"); }
        if (in.lineStart()!=null && in.lineStart()<1 || in.lineEnd()!=null && in.lineEnd()<1 || in.lineStart()!=null && in.lineEnd()!=null && in.lineEnd()<in.lineStart()) throw new RemediationException(400,"Invalid line range");
        safePath(in.filePath());
        SecurityFinding f = new SecurityFinding(in.findingId(),in.ruleId(),in.category(),severity,in.filePath(),in.lineStart(),in.lineEnd(),in.evidence(),in.explanation(),in.codeContext(),java.time.Instant.now());
        SecurityFinding old=findings.putIfAbsent(f.findingId(),f);
        if(old!=null && !(old.ruleId().equals(f.ruleId()) && old.category().equals(f.category()) && old.severity()==f.severity() && old.filePath().equals(f.filePath()) && java.util.Objects.equals(old.lineStart(),f.lineStart()) && java.util.Objects.equals(old.lineEnd(),f.lineEnd()) && old.evidence().equals(f.evidence()) && old.explanation().equals(f.explanation()) && old.codeContext().equals(f.codeContext()))) throw new RemediationException(409,"Finding ID already exists with different content");
        return old==null?f:old;
    }
    public static void safePath(String p) {
        if(p==null || p.isBlank() || p.startsWith("/") || p.startsWith("\\") || p.contains("\\") || p.matches("^[A-Za-z]:.*") || java.util.Arrays.asList(p.split("/",-1)).contains("..") || java.util.Arrays.asList(p.split("/",-1)).contains(".") || p.chars().anyMatch(Character::isISOControl)) throw new RemediationException(400,"Unsafe file path");
    }
    public SecurityFinding finding(String id){var v=findings.get(id);if(v==null)throw new RemediationException(404,"Finding not found");return v;}
    public RemediationProposal proposal(String id){var v=proposals.get(id);if(v==null)throw new RemediationException(404,"Proposal not found");return v;}
    public VerificationReport report(String id){var v=reports.get(id);if(v==null)throw new RemediationException(404,"Verification not found");return v;}
    public synchronized void save(RemediationProposal p){if(proposals.size()>=LIMIT)throw new RemediationException(507,"In-memory audit capacity reached");proposals.put(p.proposalId(),p);}
    public synchronized void save(VerificationReport r){if(reports.size()>=LIMIT)throw new RemediationException(507,"In-memory audit capacity reached");reports.put(r.verificationId(),r);}
}
