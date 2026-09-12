package com.knowledgeapplication.api.knowledge.revision.controller;

import com.knowledgeapplication.api.knowledge.dto.KnowledgeResponse;
import com.knowledgeapplication.api.knowledge.revision.dto.KnowledgeRevisionDetailResponse;
import com.knowledgeapplication.api.knowledge.revision.dto.KnowledgeRevisionPageResponse;
import com.knowledgeapplication.api.knowledge.revision.dto.KnowledgeRevisionSummaryResponse;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevision;
import com.knowledgeapplication.api.knowledge.revision.service.KnowledgeRevisionService;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/knowledge/{knowledgeId}/revisions")
public class KnowledgeRevisionController {

    private final KnowledgeRevisionService revisionService;
    private final KnowledgeService knowledgeService;

    public KnowledgeRevisionController(
            KnowledgeRevisionService revisionService,
            KnowledgeService knowledgeService
    ) {
        this.revisionService = revisionService;
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public KnowledgeRevisionPageResponse list(
            @PathVariable Long knowledgeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        var revisions = revisionService.list(knowledgeId, page, size);
        return new KnowledgeRevisionPageResponse(
                revisions.getContent().stream().map(KnowledgeRevisionController::toSummary).toList(),
                page,
                size,
                revisions.hasNext()
        );
    }

    @GetMapping("/{revisionId}")
    public KnowledgeRevisionDetailResponse detail(
            @PathVariable Long knowledgeId,
            @PathVariable Long revisionId
    ) {
        return toDetail(revisionService.get(knowledgeId, revisionId));
    }

    @PostMapping("/{revisionId}/restore")
    public KnowledgeResponse restore(
            @PathVariable Long knowledgeId,
            @PathVariable Long revisionId
    ) {
        return KnowledgeResponse.from(knowledgeService.restoreRevision(knowledgeId, revisionId));
    }

    private static KnowledgeRevisionSummaryResponse toSummary(KnowledgeRevision revision) {
        return new KnowledgeRevisionSummaryResponse(
                revision.getId(),
                revision.getCreatedAt(),
                revision.getReason(),
                revision.getTitle(),
                excerpt(revision.getSummary())
        );
    }

    private static KnowledgeRevisionDetailResponse toDetail(KnowledgeRevision revision) {
        return new KnowledgeRevisionDetailResponse(
                revision.getId(),
                revision.getTitle(),
                revision.getSummary(),
                revision.getContent(),
                revision.getCollectionName(),
                revision.getTags(),
                revision.getReason(),
                revision.getCreatedAt()
        );
    }

    private static String excerpt(String summary) {
        if (summary == null || summary.length() <= 160) {
            return summary;
        }
        return summary.substring(0, 157) + "...";
    }
}
