package com.nuno.kafkalab.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Arranca a aplicação inteira contra Postgres e Kafka em contentores (precisa do Docker a correr)
@SpringBootTest
@Import({PostgresContainerConfiguration.class, KafkaContainerConfiguration.class})
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
