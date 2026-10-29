package ai.zerotrace;

import ai.zerotrace.analysis.AnalysisException;
import ai.zerotrace.analysis.SemgrepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisIntegrationTest {
    private final SemgrepEngine engine = new SemgrepEngine("semgrep", 30);
    @Test void directFlowHasEvidence() {
        var findings = engine.analyze(Path.of("src/test/resources/fixtures/DirectUnsafe.java"));
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("zerotrace.java.sql-injection") && f.startLine() != null && f.sink() != null));
    }
    @Test void sameMethodPropagationIsAnalyzed() {
        var findings = engine.analyze(Path.of("src/test/resources/fixtures/IndirectSameMethod.java"));
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("zerotrace.java.sql-injection")));
    }
    @Test void parameterizedQueryHasNoSqlInjectionFinding() {
        var findings = engine.analyze(Path.of("src/test/resources/fixtures/ParameterizedSafe.java"));
        assertTrue(findings.stream().noneMatch(f -> f.ruleId().equals("zerotrace.java.sql-injection")));
    }
    @Test void wrapperIsReviewCueNotConfirmedTaint() {
        var findings = engine.analyze(Path.of("src/test/resources/fixtures/CustomWrapperReview.java"));
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("zerotrace.java.sql-wrapper-review") && f.status().equals("NEEDS_REVIEW")));
    }
    @Test void unrelatedStringUseIsClean() {
        var findings = engine.analyze(Path.of("src/test/resources/fixtures/HarmlessInput.java"));
        assertTrue(findings.isEmpty());
    }
    @Test void malformedJavaIsRejectedAsUnreliable() {
        assertThrows(AnalysisException.class, () -> engine.analyze(Path.of("src/test/resources/fixtures/MalformedSource.java")));
    }
    @Test void normalizedReportSerializesAsJson() throws Exception {
        var report = new ai.zerotrace.model.ScanReport("1.0", "sample", java.time.Instant.now(), "COMPLETED", 1, 0, List.of(), List.of("not proof of security"), engine.name());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
	assertEquals("sample", mapper.readTree(mapper.writeValueAsBytes(report)).path("scanId").asText());
    }
    @Test void missingAnalyzerFailsClearly() {
        assertThrows(AnalysisException.class, () -> new SemgrepEngine("missing-semgrep-binary", 1).analyze(Path.of(".")));
    }
}
