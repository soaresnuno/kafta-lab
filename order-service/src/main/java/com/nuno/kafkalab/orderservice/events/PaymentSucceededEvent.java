package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Recebido de payment-events: a encomenda foi paga
public record PaymentSucceededEvent(UUID orderId, UUID paymentId) {}
