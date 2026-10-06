package com.nuno.kafkalab.productservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Só envia depois do commit da transação atual. Assim, se a BD fizer rollback
    // (ex: o stock não chegou a ser retirado), o order-service não recebe uma confirmação falsa.
    // Ainda pode perder-se o evento se a app morrer entre o commit e o envio
    // (resolve-se com o padrão "transactional outbox", num passo futuro).
    // A key (orderId) garante que os eventos da mesma encomenda vão para a mesma partição, por ordem.
    public void publishAfterCommit(String topic, UUID key, Object event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(topic, key.toString(), event);
            }
        });
    }
}
