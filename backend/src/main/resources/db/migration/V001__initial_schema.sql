-- Flyway migration: creates the resumes table for CVForge

CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    json_data JSONB NOT NULL,
    layout VARCHAR(20) NOT NULL DEFAULT 'ats',
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER
);

CREATE INDEX idx_resumes_user_id ON resumes(user_id);
CREATE INDEX idx_resumes_layout ON resumes(layout);
CREATE INDEX idx_resumes_created_at ON resumes(created_at DESC);

COMMENT ON TABLE resumes IS 'Stores resume data in JSONB format with multiple layout support';
COMMENT ON COLUMN resumes.json_data IS 'Resume content stored as JSON for dynamic template rendering';
