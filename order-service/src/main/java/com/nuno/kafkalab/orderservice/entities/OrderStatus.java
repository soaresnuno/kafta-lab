package com.nuno.kafkalab.orderservice.entities;

public enum OrderStatus {
    // Criada, à espera da resposta do product-service
    PENDING,
    // Stock reservado
    CONFIRMED,
    // Produto inexistente ou sem stock
    REJECTED,
    // Cancelada pelo cliente; o stock (se reservado) é devolvido
    CANCELLED
}
