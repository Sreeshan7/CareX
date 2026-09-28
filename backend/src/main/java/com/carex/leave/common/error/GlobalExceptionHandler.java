package com.carex.leave.common.error;

import com.carex.leave.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/** Converts every failure into RFC 7807 ProblemDetail with a stable {@code code} (implementation.md §20.1). */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Constraint name → (status, code). Names are fixed in V1__schema.sql. */
    private static final Map<String, ApiException> CONSTRAINTS = Map.of(
            "ex_request_no_overlap", Errors.conflict("OVERLAPPING_REQUEST", "You already have leave on some of these dates"),
            "ck_balance_not_overdrawn", Errors.businessRule("INSUFFICIENT_BALANCE", "Not enough leave balance"),
            "uq_action_one_decision_per_stage", Errors.invalidTransition(null, "A decision was already recorded for this stage"),
            "uq_escalation_request_stage", Errors.conflict("ALREADY_ESCALATED", "Request already escalated at this stage"),
            "ck_request_same_year", Errors.businessRule("SPANS_YEARS", "Leave must fall within one calendar year"),
            "ck_request_dates", Errors.businessRule("INVALID_DATE_RANGE", "End date must be on or after start date"));

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> api(ApiException ex) {
        ProblemDetail pd = base(ex.getStatus(), ex.getCode(), ex.getMessage());
        ex.getExtra().forEach(pd::setProperty);
        return respond(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors);
        return respond(pd);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMediaTypeNotSupportedException.class, MultipartException.class})
    public ResponseEntity<ProblemDetail> unreadable(Exception ex) {
        return respond(base(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Malformed or unexpected request body/parameters"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException ex) {
        return respond(base(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Upload too large"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException ex) {
        return respond(base(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(NoResourceFoundException ex) {
        return respond(base(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> denied(Exception ex) {
        return respond(base(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) {
        String constraint = ConstraintNames.extract(ex);
        ApiException mapped = constraint == null ? null : CONSTRAINTS.get(constraint);
        if (mapped != null) {
            return api(mapped);
        }
        log.warn("Unmapped integrity violation constraint={}", constraint);
        return respond(base(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data"));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            CannotAcquireLockException.class})
    public ResponseEntity<ProblemDetail> concurrency(Exception ex) {
        return respond(base(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This record was modified concurrently, please refresh and retry"));
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ProblemDetail> dbDown(Exception ex) {
        log.error("Database unavailable: {}", ex.getMessage());
        return respond(base(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Service temporarily unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unknown(Exception ex, HttpServletResponse response) {
        log.error("Unhandled error", ex);
        return respond(base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error"));
    }

    public static ProblemDetail base(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return pd;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
