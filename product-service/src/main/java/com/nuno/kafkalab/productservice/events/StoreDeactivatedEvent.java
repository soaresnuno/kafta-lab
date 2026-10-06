package com.nuno.kafkalab.productservice.events;

import java.util.UUID;

// Recebido de store-events: desativar a loja e todos os produtos dela
public record StoreDeactivatedEvent(UUID storeId) {}
