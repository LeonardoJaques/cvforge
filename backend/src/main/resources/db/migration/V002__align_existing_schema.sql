-- Align the empty pre-existing database table with the API entity model.
DO $$
DECLARE id_type text;
BEGIN
  SELECT data_type INTO id_type FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'resumes' AND column_name = 'id';
  IF id_type = 'uuid' THEN
    ALTER TABLE resumes DROP COLUMN id;
    ALTER TABLE resumes ADD COLUMN id BIGSERIAL PRIMARY KEY;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='resumes' AND column_name='content') THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='resumes' AND column_name='json_data') THEN
      ALTER TABLE resumes DROP COLUMN content;
    ELSE
      ALTER TABLE resumes RENAME COLUMN content TO json_data;
    END IF;
  END IF;
END $$;

ALTER TABLE resumes DROP COLUMN IF EXISTS pdf_blob;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE resumes ALTER COLUMN created_by TYPE VARCHAR(100);
ALTER TABLE resumes ALTER COLUMN created_by DROP DEFAULT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS version INTEGER;
ALTER TABLE resumes ALTER COLUMN user_id TYPE VARCHAR(50);
ALTER TABLE resumes ALTER COLUMN title TYPE VARCHAR(200);
ALTER TABLE resumes ALTER COLUMN layout TYPE VARCHAR(20);
ALTER TABLE resumes ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE resumes ALTER COLUMN updated_at SET NOT NULL;
