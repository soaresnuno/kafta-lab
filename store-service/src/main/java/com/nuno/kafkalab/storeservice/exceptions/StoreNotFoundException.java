package com.nuno.kafkalab.storeservice.exceptions;

import java.util.UUID;

public class StoreNotFoundException extends RuntimeException {
    public StoreNotFoundException(UUID id) {
        super("Store with id " + id + " not found");
    }
}
