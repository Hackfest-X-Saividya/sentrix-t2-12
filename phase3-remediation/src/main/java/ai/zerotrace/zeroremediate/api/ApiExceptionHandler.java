package ai.zerotrace.zeroremediate.api;

import ai.zerotrace.zeroremediate.model.ApiError;
import ai.zerotrace.zeroremediate.service.RemediationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(RemediationException.class) public ResponseEntity<ApiError> remediation(RemediationException e){return ResponseEntity.status(e.status()).body(new ApiError("request_rejected",e.getMessage(),Instant.now()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(new ApiError("invalid_request","Request fields failed validation",Instant.now()));}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) public ResponseEntity<ApiError> malformed(){return ResponseEntity.badRequest().body(new ApiError("malformed_request","Request body is invalid JSON or has invalid types",Instant.now()));}
    @ExceptionHandler(Exception.class) public ResponseEntity<ApiError> other(Exception e){log.error("Unhandled Phase 3 request exception type: {}",e.getClass().getName());return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("internal_error","Request could not be completed",Instant.now()));}
}
