package com.nuno.kafkalab.productservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Postgres real num contentor Docker, criado só para os testes e apagado no fim.
// @ServiceConnection: o Spring Boot liga o datasource a este contentor (URL, user e password),
// por isso os testes não dependem do docker-compose do lab nem lhe mexem nos dados
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:15-alpine");
    }
}
