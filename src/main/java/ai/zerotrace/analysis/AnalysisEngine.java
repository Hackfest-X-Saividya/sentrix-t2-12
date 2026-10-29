package ai.zerotrace.analysis;

import ai.zerotrace.model.Finding;
import java.nio.file.Path;
import java.util.List;

public interface AnalysisEngine {
    List<Finding> analyze(Path projectRoot) throws AnalysisException;
    String name();
    String version();
}
