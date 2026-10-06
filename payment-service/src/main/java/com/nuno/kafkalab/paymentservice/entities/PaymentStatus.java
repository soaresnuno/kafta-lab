package com.nuno.kafkalab.paymentservice.entities;

public enum PaymentStatus {
    // Criado no fornecedor, à espera que o cliente pague
    PENDING,
    // Pago
    SUCCEEDED,
    // Recusado pelo fornecedor (ex: cartão recusado)
    FAILED,
    // Cancelado antes de ser pago (encomenda cancelada ou expirada)
    CANCELLED,
    // Pago e depois devolvido
    REFUNDED
}
