package com.knowledgeapplication.api.exception;

import com.knowledgeapplication.api.attachment.service.AttachmentNotFoundException;
import com.knowledgeapplication.api.attachment.service.AttachmentPersistenceException;
import com.knowledgeapplication.api.attachment.service.PublicAttachmentNotFoundException;
import com.knowledgeapplication.api.attachment.service.SharedAttachmentNotFoundException;
import com.knowledgeapplication.api.attachment.storage.ObjectStorageUnavailableException;
import com.knowledgeapplication.api.attachment.validation.ImageTooLargeException;
import com.knowledgeapplication.api.attachment.validation.MalformedImageException;
import com.knowledgeapplication.api.attachment.validation.UnsupportedImageTypeException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ApiError> handleAttachmentNotFound(AttachmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("ATTACHMENT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(PublicAttachmentNotFoundException.class)
    public ResponseEntity<ApiError> handlePublicAttachmentNotFound(PublicAttachmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .body(ApiError.of("PUBLIC_ATTACHMENT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(SharedAttachmentNotFoundException.class)
    public ResponseEntity<ApiError> handleSharedAttachmentNotFound(SharedAttachmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(ApiError.of("SHARED_ATTACHMENT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    public ResponseEntity<ApiError> handleUnsupportedImageType(UnsupportedImageTypeException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("UNSUPPORTED_IMAGE_TYPE", exception.getMessage()));
    }

    @ExceptionHandler({ImageTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiError> handleImageTooLarge(Exception exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("IMAGE_TOO_LARGE", "Image exceeds the configured upload limit"));
    }

    @ExceptionHandler(MalformedImageException.class)
    public ResponseEntity<ApiError> handleMalformedImage(MalformedImageException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("MALFORMED_IMAGE", exception.getMessage()));
    }

    @ExceptionHandler(ObjectStorageUnavailableException.class)
    public ResponseEntity<ApiError> handleObjectStorageUnavailable(ObjectStorageUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("OBJECT_STORAGE_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(AttachmentPersistenceException.class)
    public ResponseEntity<ApiError> handleAttachmentPersistence(AttachmentPersistenceException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("ATTACHMENT_PERSISTENCE_FAILED", exception.getMessage()));
    }

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
