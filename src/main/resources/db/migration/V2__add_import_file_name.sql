ALTER TABLE import_jobs ADD COLUMN file_name VARCHAR(255);

CREATE INDEX import_jobs_history_idx ON import_jobs(created_at DESC, id DESC);
