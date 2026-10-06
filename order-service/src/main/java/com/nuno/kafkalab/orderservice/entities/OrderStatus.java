package com.nuno.kafkalab.orderservice.entities;

public enum OrderStatus {
    // Criada, à espera da resposta do product-service
    PENDING,
    // Stock reservado, à espera que o cliente pague
    AWAITING_PAYMENT,
    // Paga, com o stock reservado
    CONFIRMED,
    // Produto inexistente ou sem stock
    REJECTED,
    // Pagamento recusado; o stock é devolvido
    PAYMENT_FAILED,
    // Cancelada pelo cliente ou por falta de pagamento; o stock é devolvido
    // e o pagamento cancelado (ou reembolsado, se já tinha sido pago)
    CANCELLED
}
