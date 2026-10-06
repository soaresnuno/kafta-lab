package com.nuno.kafkalab.outbox;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// Só para os testes: o @DataJpaTest precisa de uma aplicação Spring Boot como ponto de partida,
// e o módulo outbox é uma biblioteca, não tem nenhuma
@SpringBootApplication
class OutboxTestApplication {
}
