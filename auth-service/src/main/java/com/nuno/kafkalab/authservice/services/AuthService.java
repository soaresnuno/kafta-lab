package com.nuno.kafkalab.authservice.services;

import com.nuno.kafkalab.authservice.dtos.LoginRequest;
import com.nuno.kafkalab.authservice.dtos.RegisterRequest;
import com.nuno.kafkalab.authservice.keycloak.KeycloakClient;
import com.nuno.kafkalab.authservice.keycloak.KeycloakTokenResponse;
import com.nuno.kafkalab.authservice.keycloak.KeycloakUser;
import com.nuno.kafkalab.authservice.responses.RegisterResponse;
import com.nuno.kafkalab.authservice.responses.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakClient keycloakClient;

    public RegisterResponse register(RegisterRequest request) {
        // O email é também o username. emailVerified=true porque o lab não envia emails de confirmação
        KeycloakUser user = new KeycloakUser(
                request.email(),
                request.email(),
                request.firstName(),
                request.lastName(),
                true,
                true,
                List.of(new KeycloakUser.Credential("password", request.password(), false))
        );

        UUID id = keycloakClient.createUser(user);
        return new RegisterResponse(id, request.email());
    }

    public TokenResponse login(LoginRequest request) {
        KeycloakTokenResponse token = keycloakClient.login(request.email(), request.password());
        return new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }
}
