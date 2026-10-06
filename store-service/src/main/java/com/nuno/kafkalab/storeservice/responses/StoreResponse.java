package com.nuno.kafkalab.storeservice.responses;

import com.nuno.kafkalab.storeservice.entities.Store;
import com.nuno.kafkalab.storeservice.entities.StoreStatus;

import java.util.UUID;

public record StoreResponse(
        UUID id,
        String name,
        String email,
        StoreStatus status
) {
    public static StoreResponse from (Store store) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getEmail(),
                store.getStatus()
        );
    }
}
