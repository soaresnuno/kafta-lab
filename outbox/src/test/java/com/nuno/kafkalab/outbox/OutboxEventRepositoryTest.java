package com.nuno.kafkalab.outbox;

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

// O módulo não tem application.properties (uma biblioteca não deve ter: ia misturar-se com o do serviço),
// por isso o teste diz ao Hibernate para criar a tabela
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
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

        assertThat(outboxRepository.lockNextPending(10))
                .extracting(OutboxEvent::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void lockNextPendingRespectsTheBatchSize() {
        OutboxEvent first = save(event(null));
        OutboxEvent second = save(event(null));
        save(event(null));

        assertThat(outboxRepository.lockNextPending(2))
                .extracting(OutboxEvent::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void detectsAnEarlierPendingEventWithTheSameKey() {
        OutboxEvent created = save(event(null, "order-1"));
        OutboxEvent cancelled = save(event(null, "order-1"));
        OutboxEvent otherOrder = save(event(null, "order-2"));

        assertThat(outboxRepository.existsByMessageKeyAndSentAtIsNullAndIdLessThan("order-1", cancelled.getId())).isTrue();
        assertThat(outboxRepository.existsByMessageKeyAndSentAtIsNullAndIdLessThan("order-1", created.getId())).isFalse();
        assertThat(outboxRepository.existsByMessageKeyAndSentAtIsNullAndIdLessThan("order-2", otherOrder.getId())).isFalse();
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
        return event(sentAt, UUID.randomUUID().toString());
    }

    private static OutboxEvent event(Instant sentAt, String orderId) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic("order-events");
        event.setMessageKey(orderId);
        event.setType("orderCreated");
        event.setPayload("{\"orderId\":\"" + orderId + "\"}");
        event.setCreatedAt(Instant.now());
        event.setSentAt(sentAt);
        return event;
    }
}
