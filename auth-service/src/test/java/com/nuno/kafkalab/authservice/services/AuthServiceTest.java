package com.nuno.kafkalab.authservice.services;

import com.nuno.kafkalab.authservice.dtos.LoginRequest;
import com.nuno.kafkalab.authservice.dtos.RegisterRequest;
import com.nuno.kafkalab.authservice.keycloak.KeycloakClient;
import com.nuno.kafkalab.authservice.keycloak.KeycloakTokenResponse;
import com.nuno.kafkalab.authservice.keycloak.KeycloakUser;
import com.nuno.kafkalab.authservice.responses.RegisterResponse;
import com.nuno.kafkalab.authservice.responses.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private KeycloakClient keycloakClient;
    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<KeycloakUser> userCaptor;

    @Test
    void registerCreatesAnEnabledUserWithTheEmailAsUsername() {
        UUID userId = UUID.randomUUID();
        when(keycloakClient.createUser(any())).thenReturn(userId);

        RegisterResponse response = authService.register(
                new RegisterRequest("alice@kafkalab.dev", "alice-password", "Alice", "Owner"));

        assertThat(response).isEqualTo(new RegisterResponse(userId, "alice@kafkalab.dev"));
        verify(keycloakClient).createUser(userCaptor.capture());
        KeycloakUser user = userCaptor.getValue();
        assertThat(user.username()).isEqualTo("alice@kafkalab.dev");
        assertThat(user.enabled()).isTrue();
        assertThat(user.credentials())
                .containsExactly(new KeycloakUser.Credential("password", "alice-password", false));
    }

    @Test
    void loginReturnsTheAccessTokenFromKeycloak() {
        when(keycloakClient.login("alice@kafkalab.dev", "alice-password"))
                .thenReturn(new KeycloakTokenResponse("jwt", "Bearer", 900));

        TokenResponse response = authService.login(new LoginRequest("alice@kafkalab.dev", "alice-password"));

        assertThat(response).isEqualTo(new TokenResponse("jwt", "Bearer", 900));
    }
}
