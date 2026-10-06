package com.nuno.kafkalab.paymentservice.exceptions;

// Webhook com assinatura inválida, mal formado ou de um tipo que não conhecemos
public class InvalidWebhookException extends RuntimeException {
    public InvalidWebhookException(String reason) {
        super("Invalid webhook: " + reason);
    }
}
