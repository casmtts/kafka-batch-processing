package com.example.kafkabatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ErrorPageResponse(
        @JsonProperty("pagina") int page,
        @JsonProperty("tamanho") int size,
        @JsonProperty("total") long total,
        @JsonProperty("erros") List<ImportErrorResponse> errors) {
}
