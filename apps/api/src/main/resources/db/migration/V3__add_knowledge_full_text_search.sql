ALTER TABLE knowledge
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('simple'::regconfig, coalesce(title, '')), 'A') ||
            setweight(to_tsvector('simple'::regconfig, coalesce(summary, '')), 'C') ||
            setweight(to_tsvector('simple'::regconfig, coalesce(content, '')), 'D')
        ) STORED;

CREATE INDEX idx_knowledge_search_vector_gin
    ON knowledge USING GIN (search_vector);
