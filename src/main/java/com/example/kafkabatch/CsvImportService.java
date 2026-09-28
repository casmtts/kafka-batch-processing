package com.example.kafkabatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvImportService {
    private static final List<String> HEADER = List.of("sku", "nome", "preco", "estoque");
    private static final int SEND_WINDOW = 100;

    private final ImportRepository repository;
    private final ProductEventProducer producer;

    public CsvImportService(ImportRepository repository, ProductEventProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    public ImportAcceptedResponse importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCsvException("Envie um arquivo CSV não vazio no campo 'file'.");
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .build();
        try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = format.parse(reader)) {
            if (!HEADER.equals(parser.getHeaderNames())) {
                throw new InvalidCsvException("Cabeçalho esperado: sku,nome,preco,estoque.");
            }
            String fileName = safeFileName(file.getOriginalFilename());
            UUID id = repository.createJob(fileName);
            long totalRows = 0;
            List<CompletableFuture<?>> pending = new ArrayList<>(SEND_WINDOW);
            try {
                for (CSVRecord record : parser) {
                    totalRows++;
                    ProductEvent event = new ProductEvent(
                            id, record.getRecordNumber() + 1, record.size(),
                            value(record, 0), value(record, 1),
                            value(record, 2), value(record, 3));
                    pending.add(producer.send(event));
                    if (pending.size() == SEND_WINDOW) {
                        awaitSends(pending);
                    }
                }
                awaitSends(pending);
                repository.markPublished(id, totalRows);
                String status = repository.findJob(id).orElseThrow().status();
                return new ImportAcceptedResponse(id, status, fileName);
            } catch (RuntimeException ex) {
                repository.markFailed(id, "Falha ao ler ou publicar o CSV.");
                throw new PublishingException("Não foi possível concluir a publicação do CSV.", ex);
            }
        } catch (IOException | UncheckedIOException ex) {
            throw new InvalidCsvException("Não foi possível ler o arquivo CSV.");
        }
    }

    private static String value(CSVRecord record, int index) {
        return index < record.size() ? record.get(index) : "";
    }

    private static String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "arquivo.csv";
        }
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            return "arquivo.csv";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static void awaitSends(List<CompletableFuture<?>> pending) {
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
        pending.clear();
    }
}
