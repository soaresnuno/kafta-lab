package com.nuno.kafkalab.orderservice.events;

import java.math.BigDecimal;
import java.util.UUID;

// Publicado em payment-commands: pede ao payment-service que cobre a encomenda.
// É um comando (uma ordem para fazer algo), não um evento (algo que já aconteceu)
public record RequestPaymentCommand(UUID orderId, UUID userId, BigDecimal amount, String currency) {
    // Nome lógico que vai no header __TypeId__; o payment-service mapeia-o para a sua classe
    public static final String TYPE = "requestPayment";
}
