package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        UUID storeId,
        Integer quantity,
        BigDecimal unitPrice
) {
    public static OrderItemResponse from (OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getStoreId(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}
