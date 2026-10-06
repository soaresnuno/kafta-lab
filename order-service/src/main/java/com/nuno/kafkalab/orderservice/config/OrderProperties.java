package com.nuno.kafkalab.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Lê as propriedades orders.* do application.properties
@ConfigurationProperties(prefix = "orders")
public record OrderProperties(
        // Moeda de todos os preços (código ISO 4217, ex: EUR)
        String currency,
        // Tempo máximo para pagar; depois disso a encomenda é cancelada e o stock devolvido
        Duration paymentTimeout
) {
}
