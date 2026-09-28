package com.example.kafkabatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchProcessor {
    private final ImportRepository repository;

    public BatchProcessor(ImportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void process(List<ProductEvent> events) {
        Map<UUID, Counts> countsByImport = new HashMap<>();
        for (ProductEvent event : events) {
            if (!repository.startRow(event)) {
                continue;
            }
            Counts counts = countsByImport.computeIfAbsent(event.importId(), ignored -> new Counts());
            ProductValidator.ValidationResult validation = ProductValidator.validate(event);
            if (validation.error() != null) {
                repository.finishRow(event, "REJEITADA", validation.error());
                counts.rejected++;
                continue;
            }
            ProductValidator.ValidProduct product = validation.product();
            if (repository.insertProduct(product.sku(), product.name(), product.price(), product.stock())) {
                repository.finishRow(event, "IMPORTADA", null);
                counts.imported++;
            } else {
                repository.finishRow(event, "DUPLICADA", "SKU já cadastrado.");
                counts.duplicated++;
            }
        }
        countsByImport.forEach((id, counts) -> repository.addCounts(
                id, counts.imported, counts.rejected, counts.duplicated));
    }

    private static class Counts {
        long imported;
        long rejected;
        long duplicated;
    }
}
