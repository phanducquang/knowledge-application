ALTER TABLE knowledge
    ADD COLUMN share_token VARCHAR(43),
    ADD COLUMN share_token_created_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT uk_knowledge_share_token UNIQUE (share_token),
    ADD CONSTRAINT ck_knowledge_share_token_pair CHECK (
        (share_token IS NULL AND share_token_created_at IS NULL)
        OR (share_token IS NOT NULL AND share_token_created_at IS NOT NULL)
    );
