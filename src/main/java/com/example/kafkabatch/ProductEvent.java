package com.example.kafkabatch;

import java.util.UUID;

public record ProductEvent(
        UUID importId,
        long rowNumber,
        int columnCount,
        String sku,
        String name,
        String price,
        String stock) {
}
