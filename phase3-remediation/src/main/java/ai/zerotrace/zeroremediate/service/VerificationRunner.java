package ai.zerotrace.zeroremediate.service;

import ai.zerotrace.zeroremediate.model.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class VerificationRunner {
    private final FindingStore store;
    private final TrustedVerificationSuite suite;
    private final long timeoutMs;
    public VerificationRunner(FindingStore store, TrustedVerificationSuite suite, @org.springframework.beans.factory.annotation.Value("${zeroremediate.verification-timeout-ms:2000}") long timeoutMs){this.store=store;this.suite=suite;this.timeoutMs=Math.min(Math.max(timeoutMs,50),10000);}
    public VerificationReport verify(RemediationProposal p) {
        ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"trusted-verification");t.setDaemon(true);return t;});
        List<VerificationCheck> checks; List<String> limits=new ArrayList<>(); VerificationReport.Outcome outcome;
        try { checks=executor.submit(()->suite.run(p)).get(timeoutMs,TimeUnit.MILLISECONDS);
            if(checks==null || checks.isEmpty()){checks=List.of(new VerificationCheck("verification",VerificationCheck.Status.NOT_RUN,"No checks were produced.",""));limits.add("No security-specific evidence was available.");outcome=VerificationReport.Outcome.INCONCLUSIVE;}
            else if(checks.stream().anyMatch(c->c.status()==VerificationCheck.Status.FAILED)) outcome=VerificationReport.Outcome.FAILED;
            else if(checks.stream().allMatch(c->c.status()==VerificationCheck.Status.PASSED)) outcome=VerificationReport.Outcome.VERIFIED;
            else {outcome=VerificationReport.Outcome.INCONCLUSIVE;limits.add("One or more checks did not run.");}
        } catch(TimeoutException ex){checks=List.of(new VerificationCheck("trusted-fixture-checks",VerificationCheck.Status.NOT_RUN,"Verification exceeded its time limit.",""));limits.add("Timed out after "+timeoutMs+" ms; no repository code was executed.");outcome=VerificationReport.Outcome.INCONCLUSIVE;}
        catch(Exception ex){checks=List.of(new VerificationCheck("trusted-fixture-checks",VerificationCheck.Status.NOT_RUN,"Verification could not complete.",""));limits.add("Verification runner failed; no repository code was executed.");outcome=VerificationReport.Outcome.INCONCLUSIVE;}
        finally{executor.shutdownNow();}
        String claim=outcome==VerificationReport.Outcome.VERIFIED?"Defined synthetic SQL fixture checks passed.":outcome==VerificationReport.Outcome.FAILED?"At least one defined synthetic SQL fixture check failed.":"The proposed remediation could not be fully verified.";
        limits.add("This verifies only a deterministic synthetic fixture; it does not edit or inspect repository files and does not establish that a real finding is fixed.");
        VerificationReport report=new VerificationReport(UUID.randomUUID().toString(),p.proposalId(),p.findingId(),outcome,claim,"In-process predicates over trusted synthetic SQL snippets; no shell commands or untrusted source execution.",checks,limits,Instant.now());store.save(report);return report;
    }
}
