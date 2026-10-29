package ai.zerotrace.api;

import ai.zerotrace.analysis.AnalysisException;
import ai.zerotrace.model.Finding;
import ai.zerotrace.model.ScanReport;
import ai.zerotrace.service.ScanService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ScanController {
    private final ScanService scans;
    public ScanController(ScanService scans) { this.scans = scans; }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "ok", "phase", "1"); }
    @PostMapping(value="/scans", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanReport> scan(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) throw new AnalysisException("Upload is empty.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".zip"))
            throw new AnalysisException("Upload must be a .zip archive.");
        try { return ResponseEntity.status(HttpStatus.CREATED).body(scans.scan(file.getInputStream(), file.getSize())); }
        catch (java.io.IOException e) { throw new AnalysisException("Upload could not be read."); }
    }
    @GetMapping("/scans/{id}") public ResponseEntity<?> scan(@PathVariable String id) {
        ScanReport report = scans.get(id);
        return report == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(report);
    }
    @GetMapping("/scans/{id}/findings") public ResponseEntity<?> findings(@PathVariable String id) {
        ScanReport report = scans.get(id);
        return report == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(report.findings());
    }
    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<Map<String, String>> analysisError(AnalysisException e) {
        HttpStatus status = e.getMessage().contains("unavailable") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", "analysis_error", "message", e.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internalError(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal_error", "message", "The request could not be completed."));
    }
}
