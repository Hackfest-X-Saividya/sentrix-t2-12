package ai.zerotrace.ingest;

import ai.zerotrace.analysis.AnalysisException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class ProjectIngestor {
    public static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private final Path base = Path.of(System.getProperty("java.io.tmpdir"), "zerotrace-scans");

    public Path ingestZip(InputStream input, long declaredSize) {
        if (declaredSize <= 0 || declaredSize > MAX_ARCHIVE_BYTES)
            throw new AnalysisException("Archive must be between 1 byte and 20 MiB.");
        Path root = base.resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(root);
            long expanded = 0; int entries = 0;
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new AnalysisException("Archive has too many entries.");
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root) || entry.getName().startsWith("/") || entry.getName().contains("\\"))
                        throw new AnalysisException("Archive contains an unsafe path.");
                    if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                    if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".java"))
                        throw new AnalysisException("Only ZIP archives containing Java source files are supported.");
                    Files.createDirectories(target.getParent());
                    try (var out = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = zip.read(buffer)) > 0) {
                            expanded += n;
                            if (expanded > MAX_EXPANDED_BYTES) throw new AnalysisException("Expanded project exceeds 100 MiB.");
                            out.write(buffer, 0, n);
                        }
                    }
                }
            }
            try (var files = Files.walk(root)) {
                if (files.noneMatch(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")))
                    throw new AnalysisException("Archive contains no Java source files.");
            }
            return root;
        } catch (AnalysisException e) { delete(root); throw e; }
        catch (IOException e) { delete(root); throw new AnalysisException("Archive is malformed or could not be read."); }
    }

    public void delete(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
}
