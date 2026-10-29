package ai.zerotrace.zerowatch.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record RuntimeEventRequest(
        @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId,
        @NotBlank @Size(max = 8) String schemaVersion,
        @NotNull Instant timestamp,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String serviceName,
        @NotNull EventType eventType,
        @Size(max = 64) String requestId,
        @Size(max = 64) String traceId,
        @Size(max = 128) String findingId,
        @NotNull @Size(max = 8) Map<String, @NotBlank @Size(max = 64) String> attributes) { }
