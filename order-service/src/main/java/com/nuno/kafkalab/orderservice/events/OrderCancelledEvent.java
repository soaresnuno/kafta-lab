package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Publicado em order-events quando uma encomenda é cancelada; o product-service devolve o stock
public record OrderCancelledEvent(UUID orderId) {}
