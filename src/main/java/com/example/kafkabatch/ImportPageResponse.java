package com.example.kafkabatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ImportPageResponse(
        @JsonProperty("pagina") int page,
        @JsonProperty("tamanho") int size,
        @JsonProperty("total") long total,
        @JsonProperty("importacoes") List<ImportStatusResponse> imports) {
}
