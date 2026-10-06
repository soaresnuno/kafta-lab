package com.nuno.kafkalab.paymentservice.events;

import java.math.BigDecimal;
import java.util.UUID;

// Recebido de payment-commands: o order-service pede que a encomenda seja cobrada.
// É um comando (uma ordem para fazer algo), não um evento (algo que já aconteceu)
public record RequestPaymentCommand(UUID orderId, UUID userId, BigDecimal amount, String currency) {}
