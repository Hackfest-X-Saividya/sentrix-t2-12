package ai.zerotrace.zerowatch.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Optional loopback-only sender used for a controlled end-to-end synthetic demo. */
@Component
@ConditionalOnProperty(name = "zerowatch.demo.send", havingValue = "true")
public class DemoEventSender implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoEventSender.class);
    private final DemoEventProducer producer;
    private final ObjectMapper mapper;
    public DemoEventSender(DemoEventProducer producer, ObjectMapper mapper) { this.producer = producer; this.mapper = mapper; }

    @Override public void run(ApplicationArguments args) throws Exception {
        var client = HttpClient.newHttpClient();
        var endpoint = URI.create("http://127.0.0.1:8080/api/v1/zerowatch/events");
        for (var event : producer.deniedAccessSequence(Instant.now())) {
            byte[] json = mapper.writeValueAsBytes(event);
            var request = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Synthetic ZeroWatch demo event was rejected with HTTP " + response.statusCode());
            log.info("Synthetic demo event submitted; HTTP status {}", response.statusCode());
        }
    }
}
