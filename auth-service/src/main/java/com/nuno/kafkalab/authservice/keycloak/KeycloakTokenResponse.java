package com.nuno.kafkalab.authservice.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;

// Resposta do token endpoint do Keycloak (só os campos que usamos; os outros são ignorados)
public record KeycloakTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
