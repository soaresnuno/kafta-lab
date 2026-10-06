package com.nuno.kafkalab.productservice.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Publicado em stock-events: stock reservado, com o preço atual de cada produto
public record StockReservedEvent(
        UUID orderId,
        List<Item> items
) {
    public record Item(UUID productId, BigDecimal unitPrice) {}
}
