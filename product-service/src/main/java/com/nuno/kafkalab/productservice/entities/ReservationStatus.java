package com.nuno.kafkalab.productservice.entities;

public enum ReservationStatus {
    // Stock retirado aos produtos
    RESERVED,
    // Nada foi retirado (produto inexistente ou sem stock)
    REJECTED,
    // A encomenda foi cancelada e o stock foi devolvido
    RELEASED
}
