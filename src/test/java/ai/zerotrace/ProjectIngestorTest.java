package ai.zerotrace;

import ai.zerotrace.analysis.AnalysisException;
import ai.zerotrace.ingest.ProjectIngestor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectIngestorTest {
    private final ProjectIngestor ingestor = new ProjectIngestor();
    private byte[] zip(String name, String content) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        return bytes.toByteArray();
    }
    @Test void acceptsJavaZip() throws Exception {
        byte[] bytes = zip("src/Example.java", "class Example {}");
        var root = ingestor.ingestZip(new ByteArrayInputStream(bytes), bytes.length);
        try { assertTrue(java.nio.file.Files.exists(root.resolve("src/Example.java"))); }
        finally { ingestor.delete(root); }
    }
    @Test void rejectsUnsupportedFiles() throws Exception {
        byte[] bytes = zip("README.txt", "not source");
        assertThrows(AnalysisException.class, () -> ingestor.ingestZip(new ByteArrayInputStream(bytes), bytes.length));
    }
    @Test void rejectsUnsafeArchivePaths() throws Exception {
        byte[] bytes = zip("../escape.java", "class Escape {}");
        assertThrows(AnalysisException.class, () -> ingestor.ingestZip(new ByteArrayInputStream(bytes), bytes.length));
    }
    @Test void rejectsMalformedZip() {
        byte[] bytes = "not a zip".getBytes(StandardCharsets.UTF_8);
        assertThrows(AnalysisException.class, () -> ingestor.ingestZip(new ByteArrayInputStream(bytes), bytes.length));
    }
}
