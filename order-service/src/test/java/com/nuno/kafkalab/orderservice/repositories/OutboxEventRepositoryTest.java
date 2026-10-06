package com.nuno.kafkalab.orderservice.repositories;

import com.nuno.kafkalab.orderservice.PostgresContainerConfiguration;
import com.nuno.kafkalab.orderservice.entities.OutboxEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfiguration.class)
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void pendingEventsComeOutInTheOrderTheyWereSaved() {
        OutboxEvent first = save(event(null));
        save(event(Instant.now()));
        OutboxEvent second = save(event(null));

        assertThat(outboxRepository.findTop100BySentAtIsNullOrderByIdAsc())
                .extracting(OutboxEvent::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void cleanupDeletesOnlyEventsSentBeforeTheCutoff() {
        save(event(Instant.now().minus(10, DAYS)));
        OutboxEvent recent = save(event(Instant.now()));
        OutboxEvent pending = save(event(null));

        int deleted = outboxRepository.deleteSentBefore(Instant.now().minus(7, DAYS));
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxRepository.findAll())
                .extracting(OutboxEvent::getId)
                .containsExactlyInAnyOrder(recent.getId(), pending.getId());
    }

    private OutboxEvent save(OutboxEvent event) {
        return outboxRepository.saveAndFlush(event);
    }

    private static OutboxEvent event(Instant sentAt) {
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setTopic("order-events");
        event.setMessageKey(orderId.toString());
        event.setType("orderCreated");
        event.setPayload("{\"orderId\":\"" + orderId + "\"}");
        event.setCreatedAt(Instant.now());
        event.setSentAt(sentAt);
        return event;
    }
}
