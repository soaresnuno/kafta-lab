package com.nuno.kafkalab.orderservice.messaging;

import com.nuno.kafkalab.orderservice.entities.OutboxEvent;
import com.nuno.kafkalab.orderservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.orderservice.repositories.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxCaptor;

    @Test
    void savesTheEventAsJsonInTheOutboxInsteadOfSendingIt() {
        EventPublisher publisher = new EventPublisher(outboxRepository, JsonMapper.builder().build());
        UUID orderId = UUID.randomUUID();

        publisher.publish("order-events", orderId, OrderCancelledEvent.TYPE, new OrderCancelledEvent(orderId));

        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();
        assertThat(saved.getTopic()).isEqualTo("order-events");
        assertThat(saved.getMessageKey()).isEqualTo(orderId.toString());
        assertThat(saved.getType()).isEqualTo("orderCancelled");
        assertThat(saved.getPayload()).isEqualTo("{\"orderId\":\"" + orderId + "\"}");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSentAt()).isNull();
    }
}
