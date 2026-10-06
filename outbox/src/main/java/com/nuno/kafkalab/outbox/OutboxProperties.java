package com.nuno.kafkalab.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// Propriedades outbox.* que cada serviço pode mudar no seu application.properties
// (ou com variáveis de ambiente, ex: OUTBOX_POLL_INTERVAL=2s). Sem nada configurado, valem os valores por omissão.
// As durações aceitam o formato do Spring Boot: 500ms, 2s, 1h, 7d, ...
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
        // De quanto em quanto tempo o relay procura eventos por enviar (conta a partir do fim da execução anterior)
        @DefaultValue("500ms") Duration pollInterval,
        // Quantos eventos apanha de cada vez
        @DefaultValue("100") int batchSize,
        // Quanto tempo os eventos já enviados ficam na tabela antes de serem apagados
        @DefaultValue("7d") Duration retention,
        // De quanto em quanto tempo corre a limpeza dos eventos antigos
        @DefaultValue("1h") Duration cleanupInterval
) {
}
