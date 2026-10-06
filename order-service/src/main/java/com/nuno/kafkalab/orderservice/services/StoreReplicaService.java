package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.entities.StoreReplica;
import com.nuno.kafkalab.orderservice.events.StoreCreatedEvent;
import com.nuno.kafkalab.orderservice.events.StoreDeactivatedEvent;
import com.nuno.kafkalab.orderservice.repositories.StoreReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Mantém a cópia local das lojas (StoreReplica) a partir dos eventos de store-events.
// Os dois métodos são idempotentes: receber o mesmo evento duas vezes dá o mesmo resultado
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreReplicaService {

    private final StoreReplicaRepository storeRepository;

    @Transactional
    public void register(StoreCreatedEvent event) {
        if (storeRepository.existsById(event.storeId())) {
            log.info("Store {} already known, ignoring duplicate StoreCreated", event.storeId());
            return;
        }

        StoreReplica store = new StoreReplica();
        store.setId(event.storeId());
        store.setOwnerId(event.ownerId());
        store.setActive(true);
        storeRepository.save(store);
        log.info("Store {} registered", event.storeId());
    }

    @Transactional
    public void deactivate(StoreDeactivatedEvent event) {
        storeRepository.findById(event.storeId()).ifPresentOrElse(
                store -> {
                    store.setActive(false);
                    log.info("Store {} deactivated", event.storeId());
                },
                () -> log.warn("Store {} is unknown, ignoring StoreDeactivated", event.storeId()));
    }
}
