ALTER TABLE snippets
    ADD COLUMN IF NOT EXISTS owner_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE snippets SET updated_at = created_at WHERE updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_snippets_owner_id ON snippets(owner_id);
CREATE INDEX IF NOT EXISTS idx_snippets_visibility ON snippets(visibility) WHERE visibility = 'PUBLIC';