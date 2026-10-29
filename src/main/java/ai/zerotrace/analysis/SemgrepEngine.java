package ai.zerotrace.analysis;

import ai.zerotrace.model.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Controlled local adapter. Submitted application code is parsed, never executed. */
@Component
public class SemgrepEngine implements AnalysisEngine {
    private static final String RULE_VERSION = "1.0.0";
    private final String executable;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public SemgrepEngine(@Value("${zerotrace.semgrep-command:semgrep}") String executable,
                         @Value("${zerotrace.analysis-timeout-seconds:90}") long timeoutSeconds) {
        this.executable = executable;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    @Override public String name() { return "semgrep-ce"; }
    @Override public String version() { return "external (semgrep --version)"; }

    @Override public List<Finding> analyze(Path projectRoot) {
        Process process;
        Path reportFile = null;
        try {
            reportFile = java.nio.file.Files.createTempFile("zerotrace-semgrep-", ".json");
	    java.nio.file.Files.delete(reportFile);
            process = new ProcessBuilder(executable, "scan", "--config", "p/java", "--config",
        	Path.of("rules", "java-sqli.yml").toAbsolutePath().toString(),
        	"--json-output", reportFile.toAbsolutePath().toString(),
        	"--metrics=off", projectRoot.toAbsolutePath().toString())
        	.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            if (reportFile != null) try { java.nio.file.Files.deleteIfExists(reportFile); } catch (IOException ignored) { }
            throw new AnalysisException("Semgrep is unavailable. Install Semgrep CE and retry.");
        }
        try {
            boolean done = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!done) { process.destroyForcibly(); process.waitFor(); throw new AnalysisException("Static analysis exceeded its time limit."); }
            if (java.nio.file.Files.size(reportFile) > 64L * 1024 * 1024)
                throw new AnalysisException("Semgrep report exceeded the 64 MiB report limit.");
            byte[] output = java.nio.file.Files.readAllBytes(reportFile);
	    List<Finding> findings = parse(mapper.readTree(output));

		if (process.exitValue() != 0
        		&& process.exitValue() != 1
        		&& !(process.exitValue() == 2 && !findings.isEmpty())) {
    		throw new AnalysisException(
            		"Semgrep could not analyze the submitted project.");
		}

		return findings;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AnalysisException("Static analysis was interrupted.");
        } catch (IOException e) {
            throw new AnalysisException("Semgrep returned an unreadable report.");
        } finally {
            try { java.nio.file.Files.deleteIfExists(reportFile); } catch (IOException ignored) { }
        }
    }

    private List<Finding> parse(JsonNode root) {
        if (root.path("errors").isArray() && !root.path("errors").isEmpty())
            throw new AnalysisException("Semgrep reported parser or rule errors; the project could not be analyzed reliably.");
        List<Finding> findings = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            String rule = item.path("check_id").asText("unknown");
            JsonNode start = item.path("start"), end = item.path("end");
            String file = item.path("path").asText();
            int startLine = start.path("line").asInt(0), endLine = end.path("line").asInt(0);
            String code = item.path("extra").path("lines").asText("");
            boolean review = rule.contains("wrapper-review");
            String message = item.path("extra").path("message").asText("Potential SQL injection flow detected.");
            Finding.Location sink = new Finding.Location(file, startLine == 0 ? null : startLine, code);
            List<Finding.TraceStep> trace = new ArrayList<>();
            JsonNode taint = item.path("extra").path("dataflow_trace").path("taint_source");
            Finding.Location source = null;
            if (taint.isObject()) {
                JsonNode loc = taint.path("location");
                String sourceFile = loc.path("path").asText(file);
                int line = loc.path("start").path("line").asInt(0);
                String sourceCode = loc.path("lines").asText("");
                source = new Finding.Location(sourceFile, line == 0 ? null : line, sourceCode);
                if (line > 0) trace.add(new Finding.TraceStep(sourceFile, line, sourceCode));
            }
            if (startLine > 0) trace.add(new Finding.TraceStep(file, startLine, code));
            String id = rule + "-" + Integer.toHexString((file + ":" + startLine + ":" + rule).hashCode());
            findings.add(new Finding(id, rule, "sql-injection", review ? "NEEDS_REVIEW" : "POTENTIALLY_VULNERABLE",
                    file, startLine == 0 ? null : startLine, endLine == 0 ? null : endLine, source, sink,
                    trace, List.of(), review ? "MEDIUM" : "HIGH", source == null ? "LOW" : "MEDIUM",
                    message, "Inspect the source-to-sink flow and confirm whether input is attacker controlled; verify parameter binding or a context-appropriate control.",
                    name(), RULE_VERSION));
        }
        return findings;
    }
}
