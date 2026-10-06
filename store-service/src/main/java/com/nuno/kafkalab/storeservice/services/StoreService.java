package com.nuno.kafkalab.storeservice.services;

import com.nuno.kafkalab.storeservice.config.KafkaConfig;
import com.nuno.kafkalab.storeservice.dtos.CreateStoreRequest;
import com.nuno.kafkalab.storeservice.dtos.UpdateStoreRequest;
import com.nuno.kafkalab.storeservice.entities.Store;
import com.nuno.kafkalab.storeservice.entities.StoreStatus;
import com.nuno.kafkalab.storeservice.events.StoreCreatedEvent;
import com.nuno.kafkalab.storeservice.events.StoreDeactivatedEvent;
import com.nuno.kafkalab.storeservice.exceptions.StoreInactiveException;
import com.nuno.kafkalab.storeservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.storeservice.messaging.EventPublisher;
import com.nuno.kafkalab.storeservice.repositories.StoreRepository;
import com.nuno.kafkalab.storeservice.responses.StoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<StoreResponse> getAll() {
        return storeRepository.findAll().stream()
                .map(StoreResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreResponse getById(UUID id) {
        return StoreResponse.from(findOrThrow(id));
    }

    @Transactional
    public StoreResponse create(CreateStoreRequest request) {
        Store store = new Store();
        store.setName(request.name());
        store.setEmail(request.email());

        Store saved = storeRepository.save(store);
        eventPublisher.publish(KafkaConfig.STORE_EVENTS, saved.getId(), StoreCreatedEvent.TYPE,
                new StoreCreatedEvent(saved.getId(), saved.getName()));

        return StoreResponse.from(saved);
    }

    @Transactional
    public StoreResponse update(UUID id, UpdateStoreRequest request) {
        Store store = findOrThrow(id);
        if (store.getStatus() == StoreStatus.INACTIVE) {
            throw new StoreInactiveException(id);
        }

        store.setName(request.name());
        store.setEmail(request.email());
        return StoreResponse.from(store);
    }

    // Soft delete: a loja fica INACTIVE e o product-service desativa os produtos dela.
    // Desativar uma loja já inativa não faz nada (o DELETE é idempotente)
    @Transactional
    public void deactivate(UUID id) {
        Store store = findOrThrow(id);
        if (store.getStatus() == StoreStatus.INACTIVE) {
            return;
        }

        store.setStatus(StoreStatus.INACTIVE);
        eventPublisher.publish(KafkaConfig.STORE_EVENTS, id, StoreDeactivatedEvent.TYPE,
                new StoreDeactivatedEvent(id));
    }

    private Store findOrThrow(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new StoreNotFoundException(id));
    }
}
