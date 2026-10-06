package com.nuno.kafkalab.productservice.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Publicado em stock-events: stock reservado, com a loja e o preço atual de cada produto
public record StockReservedEvent(
        UUID orderId,
        List<Item> items
) {
    // Nome lógico que vai no header __TypeId__; o order-service mapeia-o para a sua classe
    public static final String TYPE = "stockReserved";

    public record Item(UUID productId, UUID storeId, BigDecimal unitPrice) {}
}
