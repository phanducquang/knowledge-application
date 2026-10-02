package com.knowledgeapplication.api.knowledge.relation;

/** Declaration order is the stable API reason order, not a relevance score. */
public enum KnowledgeRelationReason {
    WIKI_LINK,
    BACKLINK,
    SHARED_TAG,
    SAME_COLLECTION
}
