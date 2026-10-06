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

    // Cada serviço cria o tópico onde escreve. O Spring Boot (KafkaAdmin) cria-o no arranque se não existir.
    // 3 partições: a key (orderId) decide a partição, por isso os eventos da mesma encomenda ficam por ordem
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
