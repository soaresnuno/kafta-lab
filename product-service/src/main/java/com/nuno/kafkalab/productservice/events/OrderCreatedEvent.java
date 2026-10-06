package com.nuno.kafkalab.productservice.events;

import java.util.List;
import java.util.UUID;

// Recebido de order-events. Cópia própria do product-service: o contrato entre serviços é o JSON, não a classe
public record OrderCreatedEvent(
        UUID orderId,
        List<Item> items
) {
    public record Item(UUID productId, Integer quantity) {}
}
