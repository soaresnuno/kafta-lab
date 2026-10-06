package com.nuno.kafkalab.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final int BATCH_SIZE = 25;
    private static final Duration RETENTION = Duration.ofDays(30);

    @Mock
    private OutboxEventRepository outboxRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;
    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate,
                new OutboxProperties(Duration.ofMillis(500), BATCH_SIZE, RETENTION, Duration.ofHours(1)));
    }

    @Test
    void sendsPendingEventsAndMarksThemAsSent() {
        OutboxEvent created = outboxEvent(1L, "order-1", "orderCreated");
        OutboxEvent cancelled = outboxEvent(2L, "order-2", "orderCancelled");
        when(outboxRepository.lockNextPending(BATCH_SIZE)).thenReturn(List.of(created, cancelled));
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

    // Outra instância tem bloqueado o orderCreated da mesma encomenda: o orderCancelled não pode passar-lhe à frente
    @Test
    void waitsWhileAnEarlierEventWithTheSameKeyIsStillPending() {
        OutboxEvent cancelled = outboxEvent(11L, "order-1", "orderCancelled");
        when(outboxRepository.lockNextPending(BATCH_SIZE)).thenReturn(List.of(cancelled));
        when(outboxRepository.existsByMessageKeyAndSentAtIsNullAndIdLessThan("order-1", 11L)).thenReturn(true);

        relay.publishPending();

        assertThat(cancelled.getSentAt()).isNull();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void stopsAtTheFirstFailureSoEventsKeepTheirOrder() {
        OutboxEvent created = outboxEvent(1L, "order-1", "orderCreated");
        OutboxEvent cancelled = outboxEvent(2L, "order-1", "orderCancelled");
        when(outboxRepository.lockNextPending(BATCH_SIZE)).thenReturn(List.of(created, cancelled));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.publishPending();

        assertThat(created.getSentAt()).isNull();
        assertThat(cancelled.getSentAt()).isNull();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void cleanupUsesTheConfiguredRetention() {
        Instant before = Instant.now();

        relay.deleteOldSentEvents();

        verify(outboxRepository).deleteSentBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isBetween(before.minus(RETENTION), Instant.now().minus(RETENTION));
    }

    private static OutboxEvent outboxEvent(long id, String key, String type) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setTopic("order-events");
        event.setMessageKey(key);
        event.setType(type);
        event.setPayload("{\"orderId\":\"" + UUID.randomUUID() + "\"}");
        event.setCreatedAt(Instant.now());
        return event;
    }
}
