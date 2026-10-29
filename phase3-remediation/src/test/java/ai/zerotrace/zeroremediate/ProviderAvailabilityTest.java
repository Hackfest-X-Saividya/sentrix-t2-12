package ai.zerotrace.zeroremediate;

import ai.zerotrace.zeroremediate.provider.UnavailableRemediationProvider;
import ai.zerotrace.zeroremediate.model.SecurityFinding;
import ai.zerotrace.zeroremediate.model.FindingSeverity;
import ai.zerotrace.zeroremediate.service.RemediationException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProviderAvailabilityTest {
    @Test void noProviderIsExplicitlyUnavailable(){var provider=new UnavailableRemediationProvider();assertEquals("UNAVAILABLE",provider.mode());var finding=new SecurityFinding("f","r","sql-injection",FindingSeverity.HIGH,"src/A.java",1,1,List.of("e"),"x","context",Instant.now());var e=assertThrows(RemediationException.class,()->provider.propose(finding));assertEquals(503,e.status());}
}
