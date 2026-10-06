package com.nuno.kafkalab.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

// Envia para o Kafka os eventos gravados na tabela outbox_events (polling publisher).
// Registado pela OutboxAutoConfiguration; o OutboxScheduler chama os métodos de tempos a tempos.
// Pode correr em várias instâncias do mesmo serviço ao mesmo tempo (ver OutboxEventRepository.lockNextPending)
@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private static final String TYPE_HEADER = "__TypeId__";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outboxRepository.lockNextPending(properties.batchSize())) {
            // Os eventos com a mesma key (ex: a mesma encomenda) têm de sair por ordem: orderCreated antes de
            // orderCancelled. Se um anterior ainda está por enviar (bloqueado por outra instância), este fica
            // para a próxima execução. Os enviados neste ciclo já contam como enviados: o Hibernate grava-os
            // na BD antes desta query (flush automático)
            if (outboxRepository.existsByMessageKeyAndSentAtIsNullAndIdLessThan(event.getMessageKey(), event.getId())) {
                continue;
            }

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
            // O consumer usa este header para saber que record criar (ver spring.json.type.mapping)
            record.headers().add(TYPE_HEADER, event.getType().getBytes(StandardCharsets.UTF_8));

            try {
                // .get() espera pela confirmação do Kafka antes de marcar o evento como enviado
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Kafka indisponível: pára aqui para não trocar a ordem dos eventos e tenta na próxima execução.
                // Os que já foram enviados neste ciclo ficam marcados quando a transação fizer commit
                log.warn("Could not publish outbox event {} ({}), will retry: {}", event.getId(), event.getType(), e.getMessage());
                return;
            }

            // Se a app morrer entre o envio e o commit, o evento é enviado outra vez no arranque.
            // Por isso a entrega é "at least once" e os consumers têm de ignorar duplicados
            event.setSentAt(Instant.now());
        }
    }

    // Os eventos enviados ficam na tabela durante outbox.retention (dá para os consultar na BD) e depois são apagados
    @Transactional
    public void deleteOldSentEvents() {
        int deleted = outboxRepository.deleteSentBefore(Instant.now().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Deleted {} sent outbox events older than {}", deleted, properties.retention());
        }
    }
}
