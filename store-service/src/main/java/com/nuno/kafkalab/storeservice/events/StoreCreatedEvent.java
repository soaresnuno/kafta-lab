package com.nuno.kafkalab.storeservice.events;

import java.util.UUID;

// Publicado em store-events quando uma loja é criada.
// O product-service guarda uma cópia local das lojas a partir destes eventos
public record StoreCreatedEvent(UUID storeId, String name) {
    // Nome lógico que vai no header __TypeId__; quem consome mapeia-o para a sua classe
    public static final String TYPE = "storeCreated";
}
