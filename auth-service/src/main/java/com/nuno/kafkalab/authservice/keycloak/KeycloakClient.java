package com.nuno.kafkalab.authservice.keycloak;

import com.nuno.kafkalab.authservice.config.KeycloakProperties;
import com.nuno.kafkalab.authservice.exceptions.EmailAlreadyRegisteredException;
import com.nuno.kafkalab.authservice.exceptions.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.UUID;

// Fala com o Keycloak por HTTP. É o único sítio do auth-service que conhece a API do Keycloak
@Component
@RequiredArgsConstructor
public class KeycloakClient {

    private static final String TOKEN_PATH = "/realms/{realm}/protocol/openid-connect/token";
    private static final String USERS_PATH = "/admin/realms/{realm}/users";

    private final RestClient keycloakRestClient;
    private final KeycloakProperties properties;

    // Login com email e password ("password grant"). Devolve o JWT emitido pelo Keycloak
    public KeycloakTokenResponse login(String email, String password) {
        MultiValueMap<String, String> form = clientForm("password");
        form.add("username", email);
        form.add("password", password);

        return keycloakRestClient.post()
                .uri(TOKEN_PATH, properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                // 401: email ou password errados; 400: conta desativada ou incompleta
                .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.BAD_REQUEST),
                        (req, res) -> {
                            throw new InvalidCredentialsException();
                        })
                .body(KeycloakTokenResponse.class);
    }

    // Cria o utilizador e devolve o id que o Keycloak lhe deu (vai ser o "sub" dos tokens dele)
    public UUID createUser(KeycloakUser user) {
        ResponseEntity<Void> response = keycloakRestClient.post()
                .uri(USERS_PATH, properties.realm())
                .headers(headers -> headers.setBearerAuth(serviceAccountToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.CONFLICT),
                        (req, res) -> {
                            throw new EmailAlreadyRegisteredException(user.email());
                        })
                .toBodilessEntity();

        // O Keycloak responde 201 sem body, com o header Location: .../users/{id}
        String path = response.getHeaders().getLocation().getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    // Token do próprio auth-service ("client credentials"), com a permissão manage-users para usar a Admin API.
    // É pedido a cada registo para manter o código simples; com muito tráfego guardava-se até expirar
    private String serviceAccountToken() {
        return keycloakRestClient.post()
                .uri(TOKEN_PATH, properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(clientForm("client_credentials"))
                .retrieve()
                .body(KeycloakTokenResponse.class)
                .accessToken();
    }

    private MultiValueMap<String, String> clientForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }
}
