package com.example.kafkabatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record ImportStatusResponse(
        UUID id,
        String status,
        @JsonProperty("totalLinhas") long totalRows,
        @JsonProperty("linhasProcessadas") long processedRows,
        @JsonProperty("importadas") long importedRows,
        @JsonProperty("rejeitadas") long rejectedRows,
        @JsonProperty("duplicadas") long duplicateRows,
        @JsonProperty("motivoFalha") String failureReason,
        @JsonProperty("arquivo") String fileName,
        @JsonProperty("criadoEm") Instant createdAt,
        @JsonProperty("finalizadoEm") Instant finishedAt) {
}
