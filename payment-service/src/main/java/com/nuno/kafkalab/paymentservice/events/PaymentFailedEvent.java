package com.nuno.kafkalab.paymentservice.events;

import java.util.UUID;

// Publicado em payment-events: o pagamento foi recusado ou o fornecedor falhou
public record PaymentFailedEvent(UUID orderId, String reason) {
    // Nome lógico que vai no header __TypeId__; o order-service mapeia-o para a sua classe
    public static final String TYPE = "paymentFailed";
}
