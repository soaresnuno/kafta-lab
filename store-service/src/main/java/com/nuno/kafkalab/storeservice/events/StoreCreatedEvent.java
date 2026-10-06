package com.nuno.kafkalab.storeservice.events;

import java.util.UUID;

// Publicado em store-events quando uma loja é criada.
// O product-service e o order-service guardam uma cópia local das lojas a partir destes eventos,
// e usam o ownerId para saber quem pode gerir a loja sem perguntar ao store-service
public record StoreCreatedEvent(UUID storeId, UUID ownerId, String name) {
    // Nome lógico que vai no header __TypeId__; quem consome mapeia-o para a sua classe
    public static final String TYPE = "storeCreated";
}
