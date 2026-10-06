package com.nuno.kafkalab.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Usa o mesmo Binder que o Spring Boot usa para ler o application.properties
class OutboxPropertiesTest {

    @Test
    void usesTheDefaultsWhenNothingIsConfigured() {
        OutboxProperties properties = bind(Map.of());

        assertThat(properties.pollInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(properties.batchSize()).isEqualTo(100);
        assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.cleanupInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void readsTheConfiguredValues() {
        OutboxProperties properties = bind(Map.of(
                "outbox.poll-interval", "2s",
                "outbox.batch-size", "50",
                "outbox.retention", "30d"));

        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.batchSize()).isEqualTo(50);
        assertThat(properties.retention()).isEqualTo(Duration.ofDays(30));
        // O que não foi configurado fica com o valor por omissão
        assertThat(properties.cleanupInterval()).isEqualTo(Duration.ofHours(1));
    }

    private static OutboxProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("outbox", OutboxProperties.class);
    }
}
