package com.nuno.kafkalab.productservice.services;

import com.nuno.kafkalab.productservice.entities.StoreReplica;
import com.nuno.kafkalab.productservice.events.StoreCreatedEvent;
import com.nuno.kafkalab.productservice.events.StoreDeactivatedEvent;
import com.nuno.kafkalab.productservice.repositories.ProductRepository;
import com.nuno.kafkalab.productservice.repositories.StoreReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Mantém a cópia local das lojas (StoreReplica) a partir dos eventos de store-events.
// Os dois métodos são idempotentes: receber o mesmo evento duas vezes dá o mesmo resultado
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreReplicaService {

    private final StoreReplicaRepository storeRepository;
    private final ProductRepository productRepository;

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
        // Os eventos da mesma loja chegam por ordem (mesma key, mesma partição),
        // por isso uma loja desconhecida aqui não tem produtos para desativar
        Optional<StoreReplica> found = storeRepository.findById(event.storeId());
        if (found.isEmpty()) {
            log.warn("Store {} is unknown, ignoring StoreDeactivated", event.storeId());
            return;
        }
        found.get().setActive(false);

        int deactivated = productRepository.deactivateAllByStoreId(event.storeId());
        log.info("Store {} deactivated, {} products deactivated", event.storeId(), deactivated);
    }
}
