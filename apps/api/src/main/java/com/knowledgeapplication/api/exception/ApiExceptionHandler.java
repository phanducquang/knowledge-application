package com.knowledgeapplication.api.exception;

import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import com.knowledgeapplication.api.knowledge.publicview.PublicKnowledgeNotFoundException;
import com.knowledgeapplication.api.knowledge.service.UnlistedLinkNotFoundException;
import com.knowledgeapplication.api.knowledge.sharedview.SharedKnowledgeNotFoundException;
import com.knowledgeapplication.api.knowledge.revision.service.KnowledgeRevisionNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(KnowledgeNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(KnowledgeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("KNOWLEDGE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(KnowledgeRevisionNotFoundException.class)
    public ResponseEntity<ApiError> handleRevisionNotFound(KnowledgeRevisionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("KNOWLEDGE_REVISION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(PublicKnowledgeNotFoundException.class)
    public ResponseEntity<ApiError> handlePublicNotFound(PublicKnowledgeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("PUBLIC_KNOWLEDGE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(SharedKnowledgeNotFoundException.class)
    public ResponseEntity<ApiError> handleSharedNotFound(SharedKnowledgeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(ApiError.of("UNLISTED_KNOWLEDGE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(UnlistedLinkNotFoundException.class)
    public ResponseEntity<ApiError> handleUnlistedLinkNotFound(UnlistedLinkNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("UNLISTED_LINK_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors
        ));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR",
                "Request validation failed"
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR",
                "Request validation failed"
        ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleMalformedInput(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("MALFORMED_REQUEST", "Request could not be parsed"));
    }
}
