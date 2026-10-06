package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
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
                totalOf(order.getItems()),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }

    // Só há total quando todos os items têm preço, ou seja, depois de o stock ser confirmado
    private static BigDecimal totalOf(List<OrderItem> items) {
        if (items.stream().anyMatch(item -> item.getUnitPrice() == null)) {
            return null;
        }
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
