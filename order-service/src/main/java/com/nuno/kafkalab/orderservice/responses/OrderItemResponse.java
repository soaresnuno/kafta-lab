package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.OrderItem;

import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        Integer quantity
) {
    public static OrderItemResponse from (OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity()
        );
    }
}
