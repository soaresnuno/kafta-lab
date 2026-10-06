package com.nuno.kafkalab.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Lê as propriedades payments.* do application.properties
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(
        // Fornecedor usado nos pagamentos novos (ex: "fake", "stripe")
        String provider,
        Fake fake
) {
    public record Fake(
            boolean enabled,
            String checkoutBaseUrl,
            String webhookUrl,
            String webhookSecret
    ) {
    }
}
