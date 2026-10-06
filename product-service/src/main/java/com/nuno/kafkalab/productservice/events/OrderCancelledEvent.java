package com.nuno.kafkalab.productservice.events;

import java.util.UUID;

// Recebido de order-events: devolver o stock reservado para esta encomenda
public record OrderCancelledEvent(UUID orderId) {}
