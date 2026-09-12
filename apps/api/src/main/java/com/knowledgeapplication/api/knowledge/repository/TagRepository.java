package com.knowledgeapplication.api.knowledge.repository;

import com.knowledgeapplication.api.knowledge.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByOwnerIdAndNormalizedNameIn(UUID ownerId, Collection<String> normalizedNames);
}
