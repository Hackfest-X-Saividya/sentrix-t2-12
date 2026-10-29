package ai.zerotrace.zeroremediate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FindingSubmission(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String findingId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String ruleId,
        @NotBlank @Size(max = 64) String category,
        @NotBlank @Size(max = 16) String severity,
        @NotBlank @Size(max = 256) String filePath,
        Integer lineStart,
        Integer lineEnd,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 512) String> evidence,
        @NotBlank @Size(max = 1200) String explanation,
        @NotBlank @Size(max = 4096) String codeContext) { }
