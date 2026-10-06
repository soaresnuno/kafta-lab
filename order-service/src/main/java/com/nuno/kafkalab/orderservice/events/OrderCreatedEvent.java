package com.nuno.kafkalab.orderservice.events;

import java.util.List;
import java.util.UUID;

// Publicado em order-events quando uma encomenda é criada.
// O product-service tem a sua própria cópia deste record: o contrato entre serviços é o JSON, não a classe
public record OrderCreatedEvent(
        UUID orderId,
        List<Item> items
) {
    // Nome lógico que vai no header __TypeId__; o product-service mapeia-o para a sua classe
    public static final String TYPE = "orderCreated";

    public record Item(UUID productId, Integer quantity) {}
}
