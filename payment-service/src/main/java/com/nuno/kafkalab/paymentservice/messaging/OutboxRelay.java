package com.nuno.kafkalab.paymentservice.messaging;

import com.nuno.kafkalab.paymentservice.entities.OutboxEvent;
import com.nuno.kafkalab.paymentservice.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

// Envia para o Kafka os eventos gravados na tabela outbox_events (polling publisher)
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final String TYPE_HEADER = "__TypeId__";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // fixedDelay: espera 500ms depois de a execução anterior ACABAR, por isso nunca correm duas ao mesmo tempo
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outboxRepository.findTop100BySentAtIsNullOrderByIdAsc()) {
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

    // Os eventos enviados ficam 7 dias na tabela (dá para os consultar na BD) e depois são apagados
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteOldSentEvents() {
        int deleted = outboxRepository.deleteSentBefore(Instant.now().minus(7, ChronoUnit.DAYS));
        if (deleted > 0) {
            log.info("Deleted {} sent outbox events older than 7 days", deleted);
        }
    }
}
