CREATE TABLE IF NOT EXISTS runs (
    id              BIGSERIAL PRIMARY KEY,
    snippet_id      BIGINT NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms     INTEGER,
    instructions_executed INTEGER,
    exit_status     VARCHAR(20) CHECK (exit_status IN ('COMPLETED', 'ERROR', 'PAUSED', 'ABORTED')),
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_runs_user_started ON runs(user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_runs_snippet ON runs(snippet_id);
