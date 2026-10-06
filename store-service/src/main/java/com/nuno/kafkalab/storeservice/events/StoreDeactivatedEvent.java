package com.nuno.kafkalab.storeservice.events;

import java.util.UUID;

// Publicado em store-events quando uma loja é desativada; o product-service desativa os produtos dela
public record StoreDeactivatedEvent(UUID storeId) {
    // Nome lógico que vai no header __TypeId__; quem consome mapeia-o para a sua classe
    public static final String TYPE = "storeDeactivated";
}
