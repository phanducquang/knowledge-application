package com.knowledgeapplication.api.attachment.controller;

import com.knowledgeapplication.api.attachment.dto.ImageAttachmentResponse;
import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.attachment.service.KnowledgeAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge/{knowledgeId}/attachments")
public class KnowledgeAttachmentController {

    private final KnowledgeAttachmentService service;

    public KnowledgeAttachmentController(KnowledgeAttachmentService service) {
        this.service = service;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageAttachmentResponse> uploadImage(
            @PathVariable Long knowledgeId,
            @RequestPart("file") MultipartFile file
    ) {
        KnowledgeAttachment attachment = service.uploadImage(knowledgeId, file);
        return ResponseEntity.ok(new ImageAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt(),
                KnowledgeAttachmentService.markdownSource(attachment.getId())
        ));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable Long knowledgeId,
            @PathVariable UUID attachmentId
    ) {
        return AttachmentResponseSupport.inlineImage(
                service.getOwnerContent(knowledgeId, attachmentId),
                "private, no-store, max-age=0",
                true
        );
    }
}
