package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Uma encomenda vista por uma loja: só os items dessa loja e o total desses items.
// Numa encomenda com produtos de várias lojas, cada loja não vê o que foi comprado às outras
public record StoreOrderResponse(
        UUID orderId,
        UUID userId,
        OrderStatus status,
        BigDecimal total,
        List<OrderItemResponse> items
) {
    public static StoreOrderResponse from (Order order, UUID storeId) {
        List<OrderItem> storeItems = order.getItems().stream()
                .filter(item -> storeId.equals(item.getStoreId()))
                .toList();

        return new StoreOrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                Order.totalOf(storeItems),
                storeItems.stream().map(OrderItemResponse::from).toList()
        );
    }
}
