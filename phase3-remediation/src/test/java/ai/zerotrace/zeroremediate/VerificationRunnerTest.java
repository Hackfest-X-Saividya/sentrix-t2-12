package ai.zerotrace.zeroremediate;

import ai.zerotrace.zeroremediate.model.*;
import ai.zerotrace.zeroremediate.service.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VerificationRunnerTest {
    private RemediationProposal proposal(){return new RemediationProposal("p","f","FIXTURE","cause","fix","snippet","diff","why",List.of(),"regression",Instant.now());}
    @Test void failedSecurityCheckYieldsFailed(){var store=new FindingStore();var runner=new VerificationRunner(store,p->List.of(new VerificationCheck("security",VerificationCheck.Status.FAILED,"failed","counterexample")),1000);assertEquals(VerificationReport.Outcome.FAILED,runner.verify(proposal()).outcome());}
    @Test void absentChecksYieldInconclusive(){var runner=new VerificationRunner(new FindingStore(),p->List.of(),1000);assertEquals(VerificationReport.Outcome.INCONCLUSIVE,runner.verify(proposal()).outcome());}
    @Test void timeoutIsBoundedAndInconclusive(){var runner=new VerificationRunner(new FindingStore(),p->{try{Thread.sleep(1000);}catch(InterruptedException ignored){}return List.of();},50);var report=runner.verify(proposal());assertEquals(VerificationReport.Outcome.INCONCLUSIVE,report.outcome());assertTrue(report.limitations().stream().anyMatch(s->s.contains("Timed out")));}
    @Test void fixtureChecksVerifyOnlyApprovedSyntheticSnippet(){var suite=new TrustedVerificationSuite.FixtureSqlInjectionSuite();var good=new RemediationProposal("p","f","FIXTURE","cause","fix",ai.zerotrace.zeroremediate.provider.FixtureRemediationProvider.SAFE,"diff","why",List.of(),"test",Instant.now());assertTrue(suite.run(good).stream().allMatch(c->c.status()==VerificationCheck.Status.PASSED));}
}
