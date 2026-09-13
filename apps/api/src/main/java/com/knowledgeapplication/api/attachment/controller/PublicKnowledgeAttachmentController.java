package com.knowledgeapplication.api.attachment.controller;

import com.knowledgeapplication.api.attachment.service.KnowledgeAttachmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/knowledge/{slug}/attachments")
public class PublicKnowledgeAttachmentController {

    private final KnowledgeAttachmentService service;

    public PublicKnowledgeAttachmentController(KnowledgeAttachmentService service) {
        this.service = service;
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable String slug,
            @PathVariable UUID attachmentId
    ) {
        return AttachmentResponseSupport.inlineImage(
                service.getPublicContent(slug, attachmentId),
                "no-store, max-age=0",
                false
        );
    }
}
