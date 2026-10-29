package ai.zerotrace.service;

import ai.zerotrace.analysis.AnalysisEngine;
import ai.zerotrace.ingest.ProjectIngestor;
import ai.zerotrace.model.Finding;
import ai.zerotrace.model.ScanReport;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ScanService {
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final List<String> LIMITATIONS = List.of(
            "Semgrep CE Java taint analysis is intraprocedural; helper method summaries and cross-file flows are not generally traced.",
            "A clean result means no configured rule matched; it is not proof that the project is secure.",
            "Custom wrapper patterns are review cues and do not establish a source-to-sink flow.");
    private final AnalysisEngine engine;
    private final ProjectIngestor ingestor;
    private final ConcurrentHashMap<String, ScanReport> reports = new ConcurrentHashMap<>();
    public ScanService(AnalysisEngine engine, ProjectIngestor ingestor) { this.engine = engine; this.ingestor = ingestor; }

    public ScanReport scan(InputStream zip, long size) {
        Path root = ingestor.ingestZip(zip, size);
        try {
            List<Finding> findings = engine.analyze(root);
            int files;
            try (var paths = Files.walk(root)) { files = (int) paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).count(); }
            String id = UUID.randomUUID().toString();
            ScanReport report = new ScanReport("1.0", id, Instant.now(), "COMPLETED", files, findings.size(), findings, LIMITATIONS, engine.name());
            log.info("ZeroTrace scan {} completed: {} Java files, {} findings ({})", id, files, findings.size(), engine.name());
            reports.put(id, report); return report;
        } catch (java.io.IOException e) { throw new IllegalStateException("Unable to summarize scan input."); }
        finally { ingestor.delete(root); }
    }
    public ScanReport get(String id) { return reports.get(id); }
}
