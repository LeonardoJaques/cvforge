ALTER TABLE job_comparisons ADD COLUMN tailored_resume_json TEXT;
ALTER TABLE job_comparisons ADD COLUMN tailored_resume_title VARCHAR(255);
ALTER TABLE job_comparisons ADD COLUMN tailored_resume_layout VARCHAR(20);
ALTER TABLE job_comparisons ADD COLUMN tailored_resume_generated_at TIMESTAMP;
ALTER TABLE job_comparisons ADD COLUMN tailored_resume_source_hash VARCHAR(64);

CREATE INDEX idx_job_comparisons_preview_cache
    ON job_comparisons (user_id, resume_id, tailored_resume_generated_at DESC);
