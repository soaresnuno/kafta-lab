package com.nuno.kafkalab.productservice.exceptions;

import java.util.UUID;

public class StoreInactiveException extends RuntimeException {
    public StoreInactiveException(UUID id) {
        super("Store with id " + id + " is inactive");
    }
}
