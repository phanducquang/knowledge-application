CREATE TABLE ai_quota_usage (
    quota_key varchar(40) NOT NULL,
    window_start timestamptz NOT NULL,
    window_end timestamptz NOT NULL,
    request_count bigint NOT NULL DEFAULT 0 CHECK (request_count >= 0),
    estimated_input_tokens bigint NOT NULL DEFAULT 0 CHECK (estimated_input_tokens >= 0),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (quota_key, window_start),
    CHECK (window_end > window_start)
);
