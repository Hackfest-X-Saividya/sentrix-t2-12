package ai.zerotrace.zerowatch.api;

import ai.zerotrace.zerowatch.model.Alert;
import ai.zerotrace.zerowatch.model.EventIngestionResponse;
import ai.zerotrace.zerowatch.model.RuntimeEvent;
import ai.zerotrace.zerowatch.model.RuntimeEventRequest;
import ai.zerotrace.zerowatch.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@Validated
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"})
@RequestMapping("/api/v1/zerowatch")
public class ZeroWatchController {
    private final EventService service;
    public ZeroWatchController(EventService service) { this.service = service; }

    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "ok", "component", "zerowatch", "phase", "2"); }

    @PostMapping("/events")
    public ResponseEntity<EventIngestionResponse> ingest(@Valid @RequestBody RuntimeEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ingest(request));
    }

    @GetMapping("/events") public List<RuntimeEvent> events(@RequestParam(defaultValue = "100") int limit) {
        return service.latestEvents(limit);
    }
    @GetMapping("/events/{eventId}") public ResponseEntity<RuntimeEvent> event(@PathVariable String eventId) {
        return service.event(eventId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/alerts") public List<Alert> alerts(@RequestParam(defaultValue = "100") int limit) {
        return service.latestAlerts(limit);
    }
    @GetMapping("/alerts/{alertId}") public ResponseEntity<Alert> alert(@PathVariable String alertId) {
        return service.alert(alertId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
