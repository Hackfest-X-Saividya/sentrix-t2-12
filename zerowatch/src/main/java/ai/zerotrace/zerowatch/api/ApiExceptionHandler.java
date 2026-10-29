package ai.zerotrace.zerowatch.api;

import ai.zerotrace.zerowatch.model.ApiError;
import ai.zerotrace.zerowatch.service.InvalidEventException;
import ai.zerotrace.zerowatch.store.DuplicateEventException;
import ai.zerotrace.zerowatch.store.StoreCapacityException;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }

    @ExceptionHandler(InvalidEventException.class)
    ResponseEntity<ApiError> invalid(InvalidEventException e) { return error(HttpStatus.BAD_REQUEST, "invalid_event", e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> malformed(Exception e) { return error(HttpStatus.BAD_REQUEST, "invalid_request", "Request fields are missing, malformed, or outside the accepted schema."); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia() { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Use application/json for event ingestion."); }
    @ExceptionHandler(DuplicateEventException.class)
    ResponseEntity<ApiError> duplicate() { return error(HttpStatus.CONFLICT, "duplicate_event", "An event with this eventId already exists."); }
    @ExceptionHandler(StoreCapacityException.class)
    ResponseEntity<ApiError> capacity() { return error(HttpStatus.SERVICE_UNAVAILABLE, "capacity_reached", "The in-memory store is at capacity."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internal() { return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "The request could not be completed."); }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, clock.instant()));
    }
}
