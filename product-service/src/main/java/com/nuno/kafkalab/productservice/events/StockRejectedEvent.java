package com.nuno.kafkalab.productservice.events;

import java.util.UUID;

// Publicado em stock-events: produto inexistente ou sem stock suficiente
public record StockRejectedEvent(UUID orderId, String reason) {}
