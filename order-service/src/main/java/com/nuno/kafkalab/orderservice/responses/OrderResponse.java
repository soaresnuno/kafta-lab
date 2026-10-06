package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.Order;

import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        List<OrderItemResponse> items
) {
    public static OrderResponse from (Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
