package com.nuno.kafkalab.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String PAYMENT_EVENTS = "payment-events";

    // Cada serviço cria o tópico onde escreve. O Spring Boot (KafkaAdmin) cria-o no arranque se não existir.
    // 3 partições: a key (orderId) decide a partição, por isso os eventos da mesma encomenda ficam por ordem
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(PAYMENT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
