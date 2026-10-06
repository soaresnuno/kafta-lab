package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Recebido de store-events. Cópia própria do order-service: o contrato entre serviços é o JSON, não a classe
public record StoreCreatedEvent(UUID storeId, UUID ownerId, String name) {}
