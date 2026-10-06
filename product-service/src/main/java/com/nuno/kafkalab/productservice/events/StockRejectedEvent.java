package com.nuno.kafkalab.productservice.events;

import java.util.UUID;

// Publicado em stock-events: produto inexistente ou sem stock suficiente
public record StockRejectedEvent(UUID orderId, String reason) {
    // Nome lógico que vai no header __TypeId__; o order-service mapeia-o para a sua classe
    public static final String TYPE = "stockRejected";
}
