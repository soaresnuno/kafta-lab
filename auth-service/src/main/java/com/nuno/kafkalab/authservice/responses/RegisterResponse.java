package com.nuno.kafkalab.authservice.responses;

import java.util.UUID;

// id = id do utilizador no Keycloak, o mesmo que aparece no "sub" dos tokens dele
public record RegisterResponse(UUID id, String email) {}
