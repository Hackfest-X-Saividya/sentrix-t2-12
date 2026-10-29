package ai.zerotrace.zerowatch.demo;

import ai.zerotrace.zerowatch.model.EventType;
import ai.zerotrace.zerowatch.model.RuntimeEventRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Creates synthetic events for local demos and deterministic tests; it does not instrument real applications. */
@Component
public class DemoEventProducer {
    public List<RuntimeEventRequest> deniedAccessSequence(Instant start) {
        return java.util.stream.IntStream.range(0, 3).mapToObj(i -> new RuntimeEventRequest(null, "1.0", start.plusSeconds(i * 30L),
                "zerowatch-demo", EventType.ACCESS_DENIED, "demo-request-" + i, null, null,
                Map.of("actorRef", "actor-deadbeef1234", "resource", "admin-console"))).toList();
    }
}
