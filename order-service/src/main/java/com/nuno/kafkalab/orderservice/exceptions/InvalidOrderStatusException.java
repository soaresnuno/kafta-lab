package com.nuno.kafkalab.orderservice.exceptions;

import com.nuno.kafkalab.orderservice.entities.OrderStatus;

import java.util.UUID;

public class InvalidOrderStatusException extends RuntimeException {
    public InvalidOrderStatusException(UUID id, OrderStatus status) {
        super("Order with id " + id + " cannot be cancelled because it is " + status);
    }
}
