CREATE TABLE knowledge_attachment (
    id UUID PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_knowledge_attachment_object_key UNIQUE (object_key),
    CONSTRAINT fk_knowledge_attachment_knowledge
        FOREIGN KEY (knowledge_id) REFERENCES knowledge (id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_attachment_size CHECK (size_bytes > 0),
    CONSTRAINT ck_knowledge_attachment_content_type CHECK (
        content_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/gif')
    )
);

CREATE INDEX idx_knowledge_attachment_knowledge_id
    ON knowledge_attachment (knowledge_id);
