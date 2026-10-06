package com.nuno.kafkalab.authservice.keycloak;

import com.nuno.kafkalab.authservice.config.KeycloakProperties;
import com.nuno.kafkalab.authservice.exceptions.EmailAlreadyRegisteredException;
import com.nuno.kafkalab.authservice.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

// MockRestServiceServer faz de Keycloak: verifica os pedidos que o KeycloakClient envia e devolve respostas falsas
class KeycloakClientTest {

    private static final KeycloakProperties PROPERTIES =
            new KeycloakProperties("http://keycloak", "kafka-lab", "auth-service", "secret");
    private static final String TOKEN_URL = "http://keycloak/realms/kafka-lab/protocol/openid-connect/token";
    private static final String USERS_URL = "http://keycloak/admin/realms/kafka-lab/users";

    private MockRestServiceServer keycloak;
    private KeycloakClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PROPERTIES.baseUrl());
        keycloak = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakClient(builder.build(), PROPERTIES);
    }

    @Test
    void loginReturnsTheTokenIssuedByKeycloak() {
        keycloak.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "password",
                        "client_id", "auth-service",
                        "username", "alice@kafkalab.dev")))
                .andRespond(withSuccess("""
                        {"access_token": "jwt", "token_type": "Bearer", "expires_in": 900, "refresh_token": "ignored"}
                        """, MediaType.APPLICATION_JSON));

        KeycloakTokenResponse token = client.login("alice@kafkalab.dev", "alice-password");

        assertThat(token).isEqualTo(new KeycloakTokenResponse("jwt", "Bearer", 900));
        keycloak.verify();
    }

    @Test
    void loginWithWrongPasswordThrowsInvalidCredentials() {
        keycloak.expect(requestTo(TOKEN_URL))
                .andRespond(withUnauthorizedRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"invalid_grant\"}"));

        assertThatThrownBy(() -> client.login("alice@kafkalab.dev", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void createUserUsesTheServiceAccountTokenAndReturnsTheNewId() {
        UUID userId = UUID.randomUUID();
        expectServiceAccountToken();
        keycloak.expect(requestTo(USERS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(jsonPath("$.username").value("alice@kafkalab.dev"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andRespond(withCreatedEntity(URI.create(USERS_URL + "/" + userId)));

        assertThat(client.createUser(alice())).isEqualTo(userId);
        keycloak.verify();
    }

    @Test
    void createUserWithExistingEmailThrowsEmailAlreadyRegistered() {
        expectServiceAccountToken();
        keycloak.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.createUser(alice()))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    private void expectServiceAccountToken() {
        keycloak.expect(requestTo(TOKEN_URL))
                .andExpect(content().formDataContains(Map.of("grant_type", "client_credentials")))
                .andRespond(withSuccess("""
                        {"access_token": "admin-token", "token_type": "Bearer", "expires_in": 300}
                        """, MediaType.APPLICATION_JSON));
    }

    private static KeycloakUser alice() {
        return new KeycloakUser("alice@kafkalab.dev", "alice@kafkalab.dev", "Alice", "Owner", true, true,
                List.of(new KeycloakUser.Credential("password", "alice-password", false)));
    }
}
