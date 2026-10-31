ALTER TABLE job_comparisons ADD COLUMN tailored_resume_id BIGINT;
ALTER TABLE job_comparisons ADD CONSTRAINT fk_job_comparisons_tailored_resume FOREIGN KEY (tailored_resume_id) REFERENCES resumes(id) ON DELETE SET NULL;
