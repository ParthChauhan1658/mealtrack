package com.parth.mealtrack.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e, HttpServletRequest req) {
        var response = body(e.getStatus(), e.getMessage(), req);
        if (e instanceof ParseUncertainException u) {
            // Lets the dashboard show what was understood even though nothing was saved.
            var p = u.getParsed();
            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("mealsServed", p.mealsServed());
            parsed.put("date", p.date());
            parsed.put("parseMethod", p.method());
            parsed.put("confidence", p.confidence());
            response.getBody().put("parsed", parsed);
        }
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, msg, req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> unreadable(Exception e, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request body or parameter", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e, HttpServletRequest req) {
        return body(HttpStatus.CONFLICT, "Request conflicts with existing data", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "Not found", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, HttpServletRequest req) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("error", status.getReasonPhrase());
        b.put("message", message);
        b.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(b);
    }
}
