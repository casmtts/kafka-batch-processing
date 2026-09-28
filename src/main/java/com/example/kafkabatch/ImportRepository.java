package com.example.kafkabatch;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportRepository {
    private final JdbcTemplate jdbc;

    public ImportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID createJob(String fileName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO import_jobs(id, status, file_name) VALUES (?, 'PROCESSANDO', ?)",
                id, fileName);
        return id;
    }

    public void markPublished(UUID id, long totalRows) {
        jdbc.update("""
                UPDATE import_jobs SET total_rows = ?, publishing_complete = TRUE
                WHERE id = ? AND status <> 'FALHA'
                """, totalRows, id);
        finishIfComplete(id);
    }

    public void markFailed(UUID id, String reason) {
        jdbc.update("""
                UPDATE import_jobs SET status = 'FALHA', failure_reason = ?, finished_at = now()
                WHERE id = ? AND status = 'PROCESSANDO'
                """, reason, id);
    }

    public boolean startRow(ProductEvent event) {
        return jdbc.update("""
                INSERT INTO import_rows(import_id, row_number, sku, outcome)
                VALUES (?, ?, ?, 'PENDENTE')
                ON CONFLICT (import_id, row_number) DO NOTHING
                """, event.importId(), event.rowNumber(), event.sku()) == 1;
    }

    public void finishRow(ProductEvent event, String outcome, String reason) {
        jdbc.update("""
                UPDATE import_rows SET outcome = ?, reason = ?
                WHERE import_id = ? AND row_number = ?
                """, outcome, reason, event.importId(), event.rowNumber());
    }

    public boolean insertProduct(String sku, String name, BigDecimal price, int stock) {
        return jdbc.update("""
                INSERT INTO products(sku, name, price, stock) VALUES (?, ?, ?, ?)
                ON CONFLICT (sku) DO NOTHING
                """, sku, name, price, stock) == 1;
    }

    public void addCounts(UUID id, long imported, long rejected, long duplicated) {
        jdbc.update("""
                UPDATE import_jobs
                SET processed_rows = processed_rows + ?,
                    imported_rows = imported_rows + ?,
                    rejected_rows = rejected_rows + ?,
                    duplicate_rows = duplicate_rows + ?
                WHERE id = ?
                """, imported + rejected + duplicated, imported, rejected, duplicated, id);
        finishIfComplete(id);
    }

    private void finishIfComplete(UUID id) {
        jdbc.update("""
                UPDATE import_jobs
                SET status = CASE WHEN rejected_rows + duplicate_rows = 0
                                  THEN 'CONCLUIDA' ELSE 'CONCLUIDA_COM_ERROS' END,
                    finished_at = now()
                WHERE id = ? AND status = 'PROCESSANDO'
                  AND publishing_complete = TRUE AND processed_rows = total_rows
                """, id);
    }

    public Optional<ImportStatusResponse> findJob(UUID id) {
        List<ImportStatusResponse> rows = jdbc.query("""
                SELECT id, status, total_rows, processed_rows, imported_rows,
                       rejected_rows, duplicate_rows, failure_reason,
                       file_name, created_at, finished_at
                FROM import_jobs WHERE id = ?
                """, (rs, rowNum) -> mapJob(rs), id);
        return rows.stream().findFirst();
    }

    public ImportPageResponse findJobs(int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM import_jobs", Long.class);
        List<ImportStatusResponse> imports = jdbc.query("""
                SELECT id, status, total_rows, processed_rows, imported_rows,
                       rejected_rows, duplicate_rows, failure_reason,
                       file_name, created_at, finished_at
                FROM import_jobs
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> mapJob(rs), size, (long) page * size);
        return new ImportPageResponse(page, size, total == null ? 0 : total, imports);
    }

    private static ImportStatusResponse mapJob(ResultSet rs) throws SQLException {
        var finished = rs.getTimestamp("finished_at");
        return new ImportStatusResponse(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getLong("total_rows"), rs.getLong("processed_rows"),
                rs.getLong("imported_rows"), rs.getLong("rejected_rows"),
                rs.getLong("duplicate_rows"), rs.getString("failure_reason"),
                rs.getString("file_name"), rs.getTimestamp("created_at").toInstant(),
                finished == null ? null : finished.toInstant());
    }

    public ErrorPageResponse findErrors(UUID id, int page, int size) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM import_rows
                WHERE import_id = ? AND outcome IN ('REJEITADA', 'DUPLICADA')
                """, Long.class, id);
        List<ImportErrorResponse> errors = jdbc.query("""
                SELECT row_number, sku, outcome, reason FROM import_rows
                WHERE import_id = ? AND outcome IN ('REJEITADA', 'DUPLICADA')
                ORDER BY row_number LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ImportErrorResponse(
                rs.getLong("row_number"), rs.getString("sku"),
                rs.getString("outcome"), rs.getString("reason")),
                id, size, (long) page * size);
        return new ErrorPageResponse(page, size, total == null ? 0 : total, errors);
    }
}
