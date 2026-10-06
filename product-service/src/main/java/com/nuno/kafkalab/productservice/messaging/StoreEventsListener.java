package com.nuno.kafkalab.productservice.messaging;

import com.nuno.kafkalab.productservice.config.KafkaConfig;
import com.nuno.kafkalab.productservice.events.StoreCreatedEvent;
import com.nuno.kafkalab.productservice.events.StoreDeactivatedEvent;
import com.nuno.kafkalab.productservice.services.StoreReplicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Um listener por tópico. O deserializer usa o header __TypeId__ para criar o record certo,
// e o Spring chama o @KafkaHandler cujo parâmetro tem esse tipo
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaConfig.STORE_EVENTS)
public class StoreEventsListener {

    private final StoreReplicaService storeReplicaService;

    @KafkaHandler
    public void onStoreCreated(StoreCreatedEvent event) {
        storeReplicaService.register(event);
    }

    @KafkaHandler
    public void onStoreDeactivated(StoreDeactivatedEvent event) {
        storeReplicaService.deactivate(event);
    }
}
