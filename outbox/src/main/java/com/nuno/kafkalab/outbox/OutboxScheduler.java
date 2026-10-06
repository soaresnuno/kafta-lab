package com.nuno.kafkalab.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// Agenda o OutboxRelay com os intervalos do OutboxProperties. Faz o mesmo que um @Scheduled(fixedDelay = ...)
// nos métodos do relay, mas com valores vindos da configuração em vez de fixos no código.
// "Fixed delay": a próxima execução só começa depois de a anterior acabar, por isso nunca correm duas ao mesmo tempo
@RequiredArgsConstructor
class OutboxScheduler implements SchedulingConfigurer {

    // É o proxy do Spring, por isso o @Transactional dos métodos do relay continua a aplicar-se
    private final OutboxRelay relay;
    private final OutboxProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(relay::publishPending, properties.pollInterval());
        registrar.addFixedDelayTask(relay::deleteOldSentEvents, properties.cleanupInterval());
    }
}
