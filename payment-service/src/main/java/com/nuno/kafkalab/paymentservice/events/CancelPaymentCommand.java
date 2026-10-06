package com.nuno.kafkalab.paymentservice.events;

import java.util.UUID;

// Recebido de payment-commands: a encomenda foi cancelada. Se o pagamento ainda está por pagar é cancelado,
// se já foi pago é reembolsado
public record CancelPaymentCommand(UUID orderId) {}
