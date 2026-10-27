CREATE TABLE job_comparisons (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    resume_id BIGINT,
    profile_label VARCHAR(100) NOT NULL,
    job_text TEXT NOT NULL,
    result_json JSONB NOT NULL,
    language VARCHAR(2) NOT NULL DEFAULT 'pt',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_job_comparisons_user_created ON job_comparisons(user_id, created_at DESC);
