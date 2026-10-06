package com.nuno.kafkalab.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Lê as propriedades keycloak.* do application.properties (keycloak.base-url -> baseUrl, ...)
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret
) {
}
