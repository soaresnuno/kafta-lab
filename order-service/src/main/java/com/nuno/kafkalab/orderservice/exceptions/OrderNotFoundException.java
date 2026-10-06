package com.nuno.kafkalab.orderservice.exceptions;

import java.util.UUID;

public class OrderNotFoundException  extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("Order with id " + id + " not found");
    }
}
