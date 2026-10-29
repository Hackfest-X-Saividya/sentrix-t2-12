package ai.zerotrace.zeroremediate.service;

import ai.zerotrace.zeroremediate.model.*;
import ai.zerotrace.zeroremediate.provider.FixtureRemediationProvider;
import java.util.List;

public interface TrustedVerificationSuite {
    List<VerificationCheck> run(RemediationProposal proposal);
    class FixtureSqlInjectionSuite implements TrustedVerificationSuite {
        public List<VerificationCheck> run(RemediationProposal p) {
            boolean expected=p.proposedSnippet().equals(FixtureRemediationProvider.SAFE);
            boolean parameterized=p.proposedSnippet().contains("WHERE name = ?") && p.proposedSnippet().contains("setString(1, userInput)") && !p.proposedSnippet().contains("+ userInput +");
            boolean fixtureBaseline=FixtureRemediationProvider.VULNERABLE.contains("+ userInput +");
            return List.of(new VerificationCheck("synthetic-vulnerable-baseline",fixtureBaseline?VerificationCheck.Status.PASSED:VerificationCheck.Status.FAILED,"Known synthetic vulnerable fixture has concatenated SQL input.","Trusted fixture predicate evaluated in process; no user code executed."),new VerificationCheck("parameterized-remediation",expected&&parameterized?VerificationCheck.Status.PASSED:VerificationCheck.Status.FAILED,"Proposal matches the approved parameterized SQL fixture.","Placeholder and bound parameter predicates evaluated in process."));
        }
    }
}
