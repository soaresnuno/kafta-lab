package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Publicado em order-events quando uma encomenda é cancelada; o product-service devolve o stock
public record OrderCancelledEvent(UUID orderId) {
    // Nome lógico que vai no header __TypeId__; o product-service mapeia-o para a sua classe
    public static final String TYPE = "orderCancelled";
}
