package com.example.demo.contract;

import com.example.demo.adapter.in.messaging.ClaimEventKafkaConsumer;
import com.example.demo.adapter.in.websocket.ClaimWebSocketHandler;
import com.example.demo.adapter.in.websocket.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Kafka consumer contract - parses real envelopes")
class ClaimEventConsumerContractTest {

    private ClaimWebSocketHandler webSocketHandler;
    private ObjectMapper objectMapper;
    private ClaimEventKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        webSocketHandler = mock(ClaimWebSocketHandler.class);
        objectMapper = new ObjectMapper();
        consumer = new ClaimEventKafkaConsumer(webSocketHandler, objectMapper);
    }

    @Test
    @DisplayName("parses claim-submitted envelope from real publisher format")
    void parsesClaimSubmittedEnvelope() {
        String envelope = """
                {
                  "eventType": "claim-submitted",
                  "eventId": "evt-123",
                  "payload": {
                    "claimId": "11111111-1111-1111-1111-111111111111",
                    "userId": "22222222-2222-2222-2222-222222222222",
                    "correlationId": "corr-456"
                  }
                }
                """;

        assertDoesNotThrow(() -> consumer.consumeDomainEvent(envelope));

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketHandler, atLeastOnce()).broadcastToAdmins(captor.capture());

        WebSocketMessage captured = captor.getValue();
        assertEquals("CLAIM_SUBMITTED", captured.type());
        assertEquals("11111111-1111-1111-1111-111111111111", captured.claimId());
        assertEquals("SUBMITTED", captured.newStatus());
    }

    @Test
    @DisplayName("parses claim-status-changed envelope from real publisher format")
    void parsesClaimStatusChangedEnvelope() {
        String envelope = """
                {
                  "eventType": "claim-status-changed",
                  "eventId": "evt-789",
                  "payload": {
                    "claimId": "33333333-3333-3333-3333-333333333333",
                    "oldStatus": "SUBMITTED",
                    "newStatus": "UNDER_REVIEW",
                    "userId": "44444444-4444-4444-4444-444444444444",
                    "correlationId": "corr-999"
                  }
                }
                """;

        assertDoesNotThrow(() -> consumer.consumeDomainEvent(envelope));

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketHandler, atLeastOnce()).broadcastToUserAndAdmins(
                eq("44444444-4444-4444-4444-444444444444"),
                captor.capture()
        );

        WebSocketMessage captured = captor.getValue();
        assertEquals("CLAIM_STATUS_CHANGED", captured.type());
        assertEquals("33333333-3333-3333-3333-333333333333", captured.claimId());
        assertEquals("UNDER_REVIEW", captured.newStatus());
        assertEquals("SUBMITTED", captured.oldStatus());
    }

    @Test
    @DisplayName("parses CDC create envelope using real Postgres column name")
    void parsesCdcCreateEnvelope() {
        String cdcEnvelope = """
                {
                  "op": "c",
                  "after": {
                    "claim_id": "55555555-5555-5555-5555-555555555555",
                    "user_id": "66666666-6666-6666-6666-666666666666",
                    "status": "SUBMITTED"
                  },
                  "ts_ms": 1700000000000
                }
                """;

        assertDoesNotThrow(() -> consumer.consumeCdcEvent(cdcEnvelope));

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketHandler, atLeastOnce()).broadcastToAdmins(captor.capture());

        WebSocketMessage captured = captor.getValue();
        assertEquals("CLAIM_SUBMITTED", captured.type());
        assertEquals("55555555-5555-5555-5555-555555555555", captured.claimId());
    }

    @Test
    @DisplayName("parses CDC update envelope using real Postgres column names")
    void parsesCdcUpdateEnvelope() {
        String cdcEnvelope = """
                {
                  "op": "u",
                  "before": {
                    "status": "SUBMITTED"
                  },
                  "after": {
                    "claim_id": "77777777-7777-7777-7777-777777777777",
                    "user_id": "88888888-8888-8888-8888-888888888888",
                    "status": "UNDER_REVIEW"
                  },
                  "ts_ms": 1700000001000
                }
                """;

        assertDoesNotThrow(() -> consumer.consumeCdcEvent(cdcEnvelope));

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketHandler, atLeastOnce()).broadcastToUserAndAdmins(
                eq("88888888-8888-8888-8888-888888888888"),
                captor.capture()
        );

        WebSocketMessage captured = captor.getValue();
        assertEquals("CLAIM_STATUS_CHANGED", captured.type());
        assertEquals("UNDER_REVIEW", captured.newStatus());
        assertEquals("SUBMITTED", captured.oldStatus());
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}