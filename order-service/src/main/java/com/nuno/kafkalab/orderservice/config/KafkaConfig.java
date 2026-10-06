package com.nuno.kafkalab.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDER_EVENTS = "order-events";
    public static final String STOCK_EVENTS = "stock-events";
    public static final String STORE_EVENTS = "store-events";
    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String PAYMENT_EVENTS = "payment-events";

    // Cada serviço cria os tópicos onde escreve. O Spring Boot (KafkaAdmin) cria-os no arranque se não existirem.
    // 3 partições: a key (orderId) decide a partição, por isso os eventos da mesma encomenda ficam por ordem
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Comandos para o payment-service. Tópico próprio porque o product-service também lê order-events
    // e não conhece estes tipos. Pedido e cancelamento vão no mesmo tópico com a mesma key,
    // por isso o payment-service recebe-os sempre por ordem
    @Bean
    public NewTopic paymentCommandsTopic() {
        return TopicBuilder.name(PAYMENT_COMMANDS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
