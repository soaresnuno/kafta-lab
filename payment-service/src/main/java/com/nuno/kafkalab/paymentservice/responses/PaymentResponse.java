package com.nuno.kafkalab.paymentservice.responses;

import com.nuno.kafkalab.paymentservice.entities.Payment;
import com.nuno.kafkalab.paymentservice.entities.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

// checkoutUrl: para onde o cliente vai pagar enquanto o pagamento está PENDING
public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String checkoutUrl,
        String failureReason
) {
    public static PaymentResponse from (Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getCheckoutUrl(),
                payment.getFailureReason()
        );
    }
}
