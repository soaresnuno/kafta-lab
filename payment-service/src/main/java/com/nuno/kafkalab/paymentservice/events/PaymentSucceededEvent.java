package com.nuno.kafkalab.paymentservice.events;

import java.util.UUID;

// Publicado em payment-events: a encomenda foi paga
public record PaymentSucceededEvent(UUID orderId, UUID paymentId) {
    // Nome lógico que vai no header __TypeId__; o order-service mapeia-o para a sua classe
    public static final String TYPE = "paymentSucceeded";
}
