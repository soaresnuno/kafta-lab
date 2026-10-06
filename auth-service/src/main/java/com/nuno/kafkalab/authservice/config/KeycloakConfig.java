package com.nuno.kafkalab.authservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfig {

    // RestClient já apontado para o Keycloak: os pedidos só indicam o caminho (/realms/...)
    @Bean
    public RestClient keycloakRestClient(RestClient.Builder builder, KeycloakProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
