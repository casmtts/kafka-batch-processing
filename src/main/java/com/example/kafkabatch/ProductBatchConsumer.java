package com.example.kafkabatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class ProductBatchConsumer {
    private final BatchProcessor processor;
    private final ObjectMapper objectMapper;

    public ProductBatchConsumer(BatchProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            containerFactory = "batchKafkaListenerContainerFactory")
    public void listen(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        List<ProductEvent> events = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            try {
                events.add(objectMapper.readValue(record.value(), ProductEvent.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Evento Kafka inválido na posição " + record.offset(), ex);
            }
        }
        processor.process(events);
        acknowledgment.acknowledge();
    }
}
