package com.nuno.kafkalab.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

// Auto-configuração: basta um serviço ter este módulo como dependência para o outbox ficar ativo,
// como acontece com qualquer spring-boot-starter. Está registada em
// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
@AutoConfiguration(beforeName = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
// Por omissão o Spring Boot só procura entidades e repositórios no package da aplicação
// (ex: com.nuno.kafkalab.orderservice). Isto junta o package do outbox a essa lista, para o OutboxEvent
// e o OutboxEventRepository serem encontrados sem configuração nos serviços.
// Por isso tem de correr antes do JPA (o beforeName acima), que lê a lista quando arranca
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
// O OutboxRelay é um @Scheduled: sem isto nunca corria
@EnableScheduling
@Import({EventPublisher.class, OutboxRelay.class})
public class OutboxAutoConfiguration {
}
