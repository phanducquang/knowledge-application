ALTER TABLE knowledge_attachment
    ADD COLUMN orphaned_at TIMESTAMP WITH TIME ZONE;

-- Existing rows start a fresh grace period only when neither the current
-- Knowledge Markdown nor any retained revision mentions their stable reference.
UPDATE knowledge_attachment a
SET orphaned_at = CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM knowledge k
    WHERE k.id = a.knowledge_id
      AND POSITION(('attachment://' || a.id::text) IN LOWER(k.content)) > 0
)
AND NOT EXISTS (
    SELECT 1
    FROM knowledge_revision r
    WHERE r.knowledge_id = a.knowledge_id
      AND POSITION(('attachment://' || a.id::text) IN LOWER(r.content)) > 0
);

CREATE INDEX idx_knowledge_attachment_orphaned_at
    ON knowledge_attachment (orphaned_at, id)
    WHERE orphaned_at IS NOT NULL;

CREATE TABLE knowledge_attachment_delete_queue (
    object_key VARCHAR(500) PRIMARY KEY,
    queued_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_knowledge_attachment_delete_queue_queued_at
    ON knowledge_attachment_delete_queue (queued_at, object_key);
