package com.knowledgeapplication.api.attachment.controller;

import com.knowledgeapplication.api.attachment.service.AttachmentContent;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

final class AttachmentResponseSupport {

    private AttachmentResponseSupport() {
    }

    static ResponseEntity<StreamingResponseBody> inlineImage(
            AttachmentContent attachment,
            String cacheControl,
            boolean noIndex
    ) {
        StreamingResponseBody body = output -> {
            try (var input = attachment.content()) {
                input.transferTo(output);
            }
        };

        var response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.contentLength())
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(attachment.originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff");
        if (noIndex) {
            response.header("X-Robots-Tag", "noindex, nofollow, noarchive");
        }
        return response.body(body);
    }
}
