package com.nuno.kafkalab.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

// Simula duas instâncias do mesmo serviço a correr o relay ao mesmo tempo, contra um Postgres real.
// NOT_SUPPORTED: os testes não correm dentro de uma transação que é desfeita no fim; cada operação faz commit,
// como em produção, para a segunda transação (noutra thread) conseguir ver as linhas
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxLockingTest {

    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void secondInstanceSkipsTheRowsLockedByTheFirst() {
        outboxRepository.saveAll(List.of(event("a"), event("b"), event("c")));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            // Instância 1: bloqueia os 2 primeiros e mantém a transação aberta
            List<OutboxEvent> first = outboxRepository.lockNextPending(2);

            // Instância 2, noutra thread e noutra transação, enquanto a 1 ainda tem os bloqueios
            List<OutboxEvent> second = CompletableFuture
                    .supplyAsync(() -> transaction.execute(s -> outboxRepository.lockNextPending(10)))
                    .join();

            assertThat(first).extracting(OutboxEvent::getMessageKey).containsExactly("a", "b");
            // Sem SKIP LOCKED, a instância 2 ficava à espera da 1 ou apanhava as mesmas linhas
            assertThat(second).extracting(OutboxEvent::getMessageKey).containsExactly("c");
        });
    }

    @Test
    void lockedRowsAreAvailableAgainOnceTheFirstTransactionEnds() {
        outboxRepository.saveAll(List.of(event("a"), event("b")));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // A instância 1 bloqueia e termina sem marcar nada como enviado (ex: o Kafka estava em baixo)
        transaction.executeWithoutResult(status -> outboxRepository.lockNextPending(10));

        List<OutboxEvent> retry = transaction.execute(status -> outboxRepository.lockNextPending(10));
        assertThat(retry).extracting(OutboxEvent::getMessageKey).containsExactly("a", "b");
    }

    private static OutboxEvent event(String key) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic("order-events");
        event.setMessageKey(key);
        event.setType("orderCreated");
        event.setPayload("{}");
        event.setCreatedAt(Instant.now());
        return event;
    }
}
