package com.nuno.kafkalab.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

// O que os serviços usam para publicar eventos. Registado pela OutboxAutoConfiguration
@RequiredArgsConstructor
public class EventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;

    // Transactional outbox: em vez de enviar já para o Kafka, grava o evento na tabela outbox_events
    // NA MESMA transação que a alteração de negócio (ex: criar uma encomenda). Ou ficam os dois gravados, ou nenhum.
    // O OutboxRelay lê a tabela e envia para o Kafka.
    // MANDATORY: dá erro se não houver uma transação ativa, porque sem ela o evento deixava de ser atómico
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, UUID key, String type, Object event) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTopic(topic);
        outboxEvent.setMessageKey(key.toString());
        outboxEvent.setType(type);
        outboxEvent.setPayload(jsonMapper.writeValueAsString(event));
        outboxEvent.setCreatedAt(Instant.now());
        outboxRepository.save(outboxEvent);
    }
}
