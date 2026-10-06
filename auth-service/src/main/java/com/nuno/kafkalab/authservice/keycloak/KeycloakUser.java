package com.nuno.kafkalab.authservice.keycloak;

import java.util.List;

// Corpo do POST /admin/realms/{realm}/users (formato "UserRepresentation" do Keycloak)
public record KeycloakUser(
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        List<Credential> credentials
) {
    // temporary=false: a password é definitiva, o utilizador não tem de a mudar no primeiro login
    public record Credential(String type, String value, boolean temporary) {}
}
