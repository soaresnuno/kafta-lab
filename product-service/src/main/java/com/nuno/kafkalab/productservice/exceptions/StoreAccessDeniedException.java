package com.nuno.kafkalab.productservice.exceptions;

import java.util.UUID;

public class StoreAccessDeniedException extends RuntimeException {
    public StoreAccessDeniedException(UUID id) {
        super("You are not the owner of store " + id);
    }
}
