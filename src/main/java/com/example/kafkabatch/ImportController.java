package com.example.kafkabatch;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final CsvImportService importService;
    private final ImportRepository repository;

    public ImportController(CsvImportService importService, ImportRepository repository) {
        this.importService = importService;
        this.repository = repository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportAcceptedResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(importService.importFile(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportStatusResponse> status(@PathVariable UUID id) {
        return repository.findJob(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ImportPageResponse history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return repository.findJobs(page, size);
    }

    @GetMapping("/{id}/errors")
    public ResponseEntity<ErrorPageResponse> errors(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        if (repository.findJob(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(repository.findErrors(id, page, size));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Página deve ser não negativa e tamanho deve estar entre 1 e 100.");
        }
    }
}
