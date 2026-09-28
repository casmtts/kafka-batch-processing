package com.example.kafkabatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = "product-imports-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
class ImportIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("kafka_batches")
            .withUsername("kafka")
            .withPassword("kafka");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.kafka.topic", () -> "product-imports-test");
        registry.add("spring.kafka.consumer.group-id", () -> "product-imports-test-group");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ImportRepository repository;
    @Autowired BatchProcessor processor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importsValidRowsAndReportsInvalidAndDuplicateRows() throws Exception {
        String csv = """
                sku,nome,preco,estoque
                A-1,Produto A,9.99,10
                A-1,Repetido,8.00,3
                B-1,Preço ruim,abc,4
                C-1,Estoque ruim,1.00,-1
                D-1,"Produto, D",4.00,0
                """;
        UUID id = upload(csv);
        ImportStatusResponse result = waitForCompletion(id);

        assertThat(result.status()).isEqualTo("CONCLUIDA_COM_ERROS");
        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.processedRows()).isEqualTo(5);
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.rejectedRows()).isEqualTo(2);
        assertThat(result.duplicateRows()).isEqualTo(1);
        assertThat(result.fileName()).isEqualTo("produtos.csv");
        assertThat(result.createdAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE sku IN ('A-1', 'D-1')", Long.class))
                .isEqualTo(2);

        mvc.perform(get("/api/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivo").value("produtos.csv"))
                .andExpect(jsonPath("$.importadas").value(2))
                .andExpect(jsonPath("$.rejeitadas").value(2))
                .andExpect(jsonPath("$.duplicadas").value(1));
        mvc.perform(get("/api/imports/{id}/errors", id).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.erros.length()").value(2));
        mvc.perform(get("/api/imports").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.importacoes").isArray());
        assertThat(repository.findJobs(0, 10).imports())
                .anyMatch(job -> job.id().equals(id) && "produtos.csv".equals(job.fileName()));

        processor.process(List.of(new ProductEvent(id, 2, 4, "A-1", "Produto A", "9.99", "10")));
        ImportStatusResponse afterRedelivery = repository.findJob(id).orElseThrow();
        assertThat(afterRedelivery.processedRows()).isEqualTo(5);
        assertThat(afterRedelivery.importedRows()).isEqualTo(2);
        assertThat(afterRedelivery.duplicateRows()).isEqualTo(1);
    }

    @Test
    void rejectsWrongHeaderBeforeCreatingJob() throws Exception {
        Long before = jdbc.queryForObject("SELECT count(*) FROM import_jobs", Long.class);
        MockMultipartFile file = csvFile("codigo,nome,preco,estoque\nA-1,Produto,1.00,1\n");
        mvc.perform(multipart("/api/imports").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Cabeçalho esperado: sku,nome,preco,estoque."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_jobs", Long.class)).isEqualTo(before);
    }

    private UUID upload(String csv) throws Exception {
        String response = mvc.perform(multipart("/api/imports").file(csvFile(csv)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return UUID.fromString(body.get("id").asText());
    }

    private MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "produtos.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private ImportStatusResponse waitForCompletion(UUID id) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            ImportStatusResponse result = repository.findJob(id).orElseThrow();
            if (result.status().startsWith("CONCLUIDA")) {
                return result;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("A importação não terminou em 20 segundos: " + repository.findJob(id));
    }
}
