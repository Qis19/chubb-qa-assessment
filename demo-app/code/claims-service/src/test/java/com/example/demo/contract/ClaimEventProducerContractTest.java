package com.example.demo.contract;

import com.example.demo.adapter.out.messaging.KafkaDomainEventPublisher;
import com.example.demo.adapter.out.messaging.KafkaMessageSchemaValidator;
import com.example.demo.adapter.out.messaging.KafkaMessageValidationException;
import com.example.demo.domain.claim.events.ClaimStatusChanged;
import com.example.demo.domain.claim.events.ClaimSubmitted;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Kafka producer contract - envelope vs committed schema")
class ClaimEventProducerContractTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private KafkaMessageSchemaValidator schemaValidator;
    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        // Real validator - uses the committed JSON schemas on the classpath
        schemaValidator = new KafkaMessageSchemaValidator(objectMapper, true);
        publisher = new KafkaDomainEventPublisher(kafkaTemplate, objectMapper, schemaValidator);
    }

    @Test
    @DisplayName("BUG: ClaimSubmitted envelope does not match its committed schema")
    void claimSubmittedEnvelopeViolatesSchema() {
        var event = new ClaimSubmitted(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now().minusDays(1),
                new BigDecimal("5000"),
                Instant.now()
        );

        // The real schema requires top-level: eventId, claimId, userId, incidentDate, claimAmount, occurredAt
        // The publisher builds: { eventType, eventId, payload: { claimId, userId, correlationId } }
        // Validation will fail - documenting the contract violation
        KafkaMessageValidationException exception = assertThrows(
                KafkaMessageValidationException.class,
                () -> publisher.publish(event)
        );

        assertTrue(
                exception.getMessage().contains("ClaimSubmittedMessage"),
                "Expected schema validation failure for ClaimSubmittedMessage"
        );
    }

    @Test
    @DisplayName("BUG: ClaimStatusChanged envelope does not match its committed schema")
    void claimStatusChangedEnvelopeViolatesSchema() {
        var event = new ClaimStatusChanged(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                com.example.demo.domain.claim.ClaimStatus.SUBMITTED,
                com.example.demo.domain.claim.ClaimStatus.UNDER_REVIEW,
                UUID.randomUUID(),
                Instant.now()
        );

        // Same mismatch - envelope has eventType + payload wrapper, schema expects flat
        KafkaMessageValidationException exception = assertThrows(
                KafkaMessageValidationException.class,
                () -> publisher.publish(event)
        );

        assertTrue(
                exception.getMessage().contains("ClaimStatusChangedMessage"),
                "Expected schema validation failure for ClaimStatusChangedMessage"
        );
    }
}