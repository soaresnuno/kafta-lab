package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Recebido de payment-events: o pagamento foi recusado
public record PaymentFailedEvent(UUID orderId, String reason) {}
