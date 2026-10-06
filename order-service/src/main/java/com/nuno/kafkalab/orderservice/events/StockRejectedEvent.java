package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Recebido de stock-events: produto inexistente ou sem stock suficiente
public record StockRejectedEvent(UUID orderId, String reason) {}
