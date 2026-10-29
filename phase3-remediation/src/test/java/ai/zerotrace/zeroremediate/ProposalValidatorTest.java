package ai.zerotrace.zeroremediate;

import ai.zerotrace.zeroremediate.model.ProposalDraft;
import ai.zerotrace.zeroremediate.provider.FixtureRemediationProvider;
import ai.zerotrace.zeroremediate.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProposalValidatorTest {
    private final ProposalValidator validator=new ProposalValidator();
    @Test void acceptsWellFormedSingleFilePatch(){var d=new ProposalDraft("root","fix",FixtureRemediationProvider.SAFE,"--- a/src/UserDao.java\n+++ b/src/UserDao.java\n@@ -1,2 +1,4 @@\n-old\n+new","reason",List.of("synthetic"),"test");assertDoesNotThrow(()->validator.validate(d,"src/UserDao.java"));}
    @Test void rejectsMalformedProviderResponse(){assertThrows(RemediationException.class,()->validator.validate(null,"src/UserDao.java"));}
    @Test void rejectsPatchTargetingTraversalPath(){var d=new ProposalDraft("a","b","c","--- a/src/UserDao.java\n+++ b/../../outside.java\n@@ -1 +1 @@\n-a\n+b","r",List.of(),"test");assertThrows(RemediationException.class,()->validator.validate(d,"src/UserDao.java"));}
    @Test void rejectsMultiFilePatch(){var d=new ProposalDraft("a","b","c","--- a/src/UserDao.java\n+++ b/src/UserDao.java\n@@ -1 +1 @@\n-a\n+b\n--- a/other.java\n+++ b/other.java\n@@ -1 +1 @@\n-a\n+b","r",List.of(),"test");assertThrows(RemediationException.class,()->validator.validate(d,"src/UserDao.java"));}
}
