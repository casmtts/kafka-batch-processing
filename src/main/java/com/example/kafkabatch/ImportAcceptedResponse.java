package com.example.kafkabatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ImportAcceptedResponse(
        UUID id,
        @JsonProperty("status") String status,
        @JsonProperty("arquivo") String fileName) {
}
