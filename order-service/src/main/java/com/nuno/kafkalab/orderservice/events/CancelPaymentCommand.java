package com.nuno.kafkalab.orderservice.events;

import java.util.UUID;

// Publicado em payment-commands quando uma encomenda com pagamento pedido é cancelada:
// o payment-service cancela o pagamento ou, se já foi pago, reembolsa-o
public record CancelPaymentCommand(UUID orderId) {
    // Nome lógico que vai no header __TypeId__; o payment-service mapeia-o para a sua classe
    public static final String TYPE = "cancelPayment";
}
