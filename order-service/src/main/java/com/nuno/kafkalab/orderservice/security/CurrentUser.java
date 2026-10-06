package com.nuno.kafkalab.orderservice.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    // O "sub" do token é o id do utilizador no Keycloak
    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
