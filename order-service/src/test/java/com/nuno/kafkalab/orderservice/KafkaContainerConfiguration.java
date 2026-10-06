package com.nuno.kafkalab.orderservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

// Kafka real num contentor Docker, só para os testes. Sem isto, a aplicação de teste ligava-se ao
// Kafka do lab e entrava nos mesmos consumer groups do serviço verdadeiro, podendo consumir os eventos dele
@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainerConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer("apache/kafka:4.1.1");
    }
}
