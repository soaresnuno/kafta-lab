package com.nuno.kafkalab.storeservice.entities;

public enum StoreStatus {
    ACTIVE,
    // "Apagada": continua na BD porque produtos e encomendas antigas apontam para ela
    INACTIVE
}
