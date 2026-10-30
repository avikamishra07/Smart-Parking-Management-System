package com.rcoem.parking.exception;

import com.rcoem.parking.dto.ApiError;
import com.rcoem.parking.dto.ApiError.FieldIssue;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Single place that turns every exception into the standard {@link ApiError} JSON body.
 *
 * <ul>
 *   <li>400 - Bean Validation failures, malformed JSON, bad parameter types, invalid parking times</li>
 *   <li>404 - unknown slot / booking (and unknown URLs)</li>
 *   <li>409 - occupied slot, duplicate checkout / payment, concurrent-update and constraint conflicts</li>
 *   <li>500 - anything unexpected; the client only sees a generic message, the stack trace is logged</li>
 * </ul>
 *
 * It extends {@link ResponseEntityExceptionHandler} so Spring's own MVC exceptions (405 wrong method,
 * 415 wrong content type, missing request parameter, ...) keep their correct status instead of being
 * swallowed by the catch-all 500 handler, and they are rendered in the same {@link ApiError} shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 404

    @ExceptionHandler({SlotNotFoundException.class, BookingNotFoundException.class})
    public ResponseEntity<Object> handleNotFound(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of(), null);
    }

    // ---------------------------------------------------------------- 409

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<Object> handleConflict(BookingConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of(), null);
    }

    /** Optimistic/pessimistic lock failures and deadlocks: the request lost a race, retrying may work. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrencyFailure(ConcurrencyFailureException ex, WebRequest request) {
        log.warn("Concurrent update conflict: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT,
                "The resource was modified by another request. Please retry.", request, List.of(), null);
    }

    /** Database constraint hit (e.g. the partial unique indexes). SQL / constraint names are not exposed. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT,
                "The request conflicts with the current state of the data.", request, List.of(), null);
    }

    // ---------------------------------------------------------------- 400

    @ExceptionHandler(InvalidParkingTimeException.class)
    public ResponseEntity<Object> handleInvalidParkingTime(InvalidParkingTimeException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of(), null);
    }

    /** Thrown when Bean Validation runs on a @Validated bean (not used by the controllers today, but safe). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldIssue> details = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            details.add(new FieldIssue(String.valueOf(v.getPropertyPath()), v.getMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details, null);
    }

    /** @Valid @RequestBody failures, e.g. blank vehicleNumber or missing slotId. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldIssue> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(new FieldIssue(fe.getField(), fe.getDefaultMessage()));
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            details.add(new FieldIssue(oe.getObjectName(), oe.getDefaultMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details, headers);
    }

    /** Constraints on method parameters, e.g. @Positive on a path variable ({@code /slots/0}). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldIssue> details = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            if (name == null) {
                name = "arg" + result.getMethodParameter().getParameterIndex();
            }
            if (result instanceof ParameterErrors errors) {
                for (FieldError fe : errors.getFieldErrors()) {
                    details.add(new FieldIssue(fe.getField(), fe.getDefaultMessage()));
                }
            } else {
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    details.add(new FieldIssue(name, error.getDefaultMessage()));
                }
            }
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details, headers);
    }

    /** Invalid JSON, wrong field types, empty body. Parser internals are deliberately not echoed. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request, List.of(), headers);
    }

    /** e.g. /slots/abc or /reports/revenue?date=not-a-date. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        String message = name != null ? "Invalid value for parameter '" + name + "'" : "Invalid parameter value";
        return build(HttpStatus.BAD_REQUEST, message, request, List.of(), headers);
    }

    // ---------------------------------------------------------------- 500

    /** Last resort. Full details go to the log only; the client gets a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", request, List.of(), null);
    }

    // ------------------------------------------- other Spring MVC exceptions (405, 415, 404 URL, missing param, ...)

    /**
     * Every standard MVC exception handled by the parent class ends up here; we replace Spring's
     * ProblemDetail body with our ApiError while keeping the status and headers (e.g. "Allow" on 405).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String detail = null;
        if (body instanceof ProblemDetail pd) {
            detail = pd.getDetail();
        } else if (ex instanceof ErrorResponse errorResponse) {   // Spring passes a null body for 405, 415, ...
            detail = errorResponse.getBody().getDetail();
        }
        String message = detail != null ? detail : reason(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("Server error on {}", path(request), ex);
            message = "An unexpected error occurred. Please try again later.";
        }
        return build(statusCode, message, request, List.of(), headers);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<Object> build(HttpStatusCode status, String message, WebRequest request,
                                         List<FieldIssue> details, HttpHeaders headers) {
        ApiError body = new ApiError(LocalDateTime.now(clock), status.value(), reason(status),
                message, path(request), details);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(body);
    }

    private static String reason(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false).replaceFirst("^uri=", "");
    }
}
