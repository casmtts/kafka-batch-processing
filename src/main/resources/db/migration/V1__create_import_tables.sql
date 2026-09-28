CREATE TABLE import_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    total_rows BIGINT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    imported_rows BIGINT NOT NULL DEFAULT 0,
    rejected_rows BIGINT NOT NULL DEFAULT 0,
    duplicate_rows BIGINT NOT NULL DEFAULT 0,
    publishing_complete BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE TABLE products (
    sku VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(18, 2) NOT NULL CHECK (price >= 0),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE import_rows (
    import_id UUID NOT NULL REFERENCES import_jobs(id),
    row_number BIGINT NOT NULL,
    sku TEXT,
    outcome VARCHAR(30) NOT NULL,
    reason TEXT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (import_id, row_number)
);

CREATE INDEX import_rows_errors_idx ON import_rows(import_id, row_number)
    WHERE outcome IN ('REJEITADA', 'DUPLICADA');
