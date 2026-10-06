package com.nuno.kafkalab.paymentservice.gateway;

import java.math.BigDecimal;
import java.util.UUID;

// O orderId serve de chave de idempotência no fornecedor: o mesmo pedido enviado duas vezes não cobra duas vezes
public record PaymentRequest(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency
) {
}
