package com.nuno.kafkalab.paymentservice.exceptions;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID orderId) {
        super("Payment for order " + orderId + " not found");
    }
}
