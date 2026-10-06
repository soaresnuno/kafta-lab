package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Recebido de store-events: a loja foi desativada
public record StoreDeactivatedEvent(UUID storeId) {}
