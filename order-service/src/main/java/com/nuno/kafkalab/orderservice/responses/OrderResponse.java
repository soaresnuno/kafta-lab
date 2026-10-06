package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        OrderStatus status,
        String rejectionReason,
        BigDecimal total,
        List<OrderItemResponse> items
) {
    public static OrderResponse from (Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getRejectionReason(),
                order.getTotal(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
