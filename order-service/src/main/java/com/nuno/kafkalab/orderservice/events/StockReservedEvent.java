package com.nuno.kafkalab.orderservice.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Recebido de stock-events: o product-service reservou o stock e diz a loja e o preço de cada produto
public record StockReservedEvent(
        UUID orderId,
        List<Item> items
) {
    public record Item(UUID productId, UUID storeId, BigDecimal unitPrice) {}
}
