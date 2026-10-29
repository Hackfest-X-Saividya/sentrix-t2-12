package ai.zerotrace.zerowatch.model;

import java.time.Instant;

public record ApiError(String error, String message, Instant timestamp) { }
