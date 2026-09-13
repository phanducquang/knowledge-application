package com.knowledgeapplication.api.attachment.repository;

import com.knowledgeapplication.api.attachment.model.AttachmentDeleteQueueEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentDeleteQueueRepository extends JpaRepository<AttachmentDeleteQueueEntry, String> {

    List<AttachmentDeleteQueueEntry> findAllByOrderByQueuedAtAscObjectKeyAsc(Pageable pageable);
}
