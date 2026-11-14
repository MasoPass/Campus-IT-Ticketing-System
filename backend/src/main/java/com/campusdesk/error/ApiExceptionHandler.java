package com.campusdesk.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Central place that converts exceptions into JSON error responses
 * (RFC 7807 "problem detail": {"status":404,"detail":"Project 9 not found",...}).
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadArg(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooBig(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large");
    }

    /**
     * SQLite isn't in Spring's built-in error-code table, so constraint violations arrive
     * as a generic DataAccessException. We look at the driver's message to map the two
     * we care about to proper HTTP codes.
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDb(DataAccessException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage());
        if (msg.contains("UNIQUE constraint failed")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "That already exists");
        }
        if (msg.contains("FOREIGN KEY constraint failed")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Referenced item does not exist");
        }
        log.error("Database error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Database error");
    }
}
