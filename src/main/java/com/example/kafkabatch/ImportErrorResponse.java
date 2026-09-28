package com.example.kafkabatch;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImportErrorResponse(
        @JsonProperty("linha") long rowNumber,
        String sku,
        @JsonProperty("resultado") String outcome,
        @JsonProperty("motivo") String reason) {
}
