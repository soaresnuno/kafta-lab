package com.nuno.kafkalab.orderservice.messaging;

import com.nuno.kafkalab.orderservice.entities.OutboxEvent;
import com.nuno.kafkalab.orderservice.repositories.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @InjectMocks
    private OutboxRelay relay;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    @Test
    void sendsPendingEventsAndMarksThemAsSent() {
        OutboxEvent created = outboxEvent(1L, "orderCreated");
        OutboxEvent cancelled = outboxEvent(2L, "orderCancelled");
        when(outboxRepository.findTop100BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(created, cancelled));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        assertThat(created.getSentAt()).isNotNull();
        assertThat(cancelled.getSentAt()).isNotNull();

        verify(kafkaTemplate, times(2)).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getAllValues().getFirst();
        assertThat(record.topic()).isEqualTo(created.getTopic());
        assertThat(record.key()).isEqualTo(created.getMessageKey());
        assertThat(record.value()).isEqualTo(created.getPayload());
        assertThat(new String(record.headers().lastHeader("__TypeId__").value(), UTF_8)).isEqualTo("orderCreated");
    }

    @Test
    void stopsAtTheFirstFailureSoEventsKeepTheirOrder() {
        OutboxEvent created = outboxEvent(1L, "orderCreated");
        OutboxEvent cancelled = outboxEvent(2L, "orderCancelled");
        when(outboxRepository.findTop100BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(created, cancelled));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.publishPending();

        assertThat(created.getSentAt()).isNull();
        assertThat(cancelled.getSentAt()).isNull();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    private static OutboxEvent outboxEvent(long id, String type) {
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setTopic("order-events");
        event.setMessageKey(orderId.toString());
        event.setType(type);
        event.setPayload("{\"orderId\":\"" + orderId + "\"}");
        event.setCreatedAt(Instant.now());
        return event;
    }
}
