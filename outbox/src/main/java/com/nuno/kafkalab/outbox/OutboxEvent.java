package com.nuno.kafkalab.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Um evento à espera de ser enviado para o Kafka (transactional outbox).
// É gravado na mesma transação que a alteração de negócio; o OutboxRelay envia-o depois.
// Cada serviço tem a sua tabela outbox_events, na sua própria base de dados: partilha-se o código, não os dados
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
    // IDENTITY: número sequencial, dá a ordem pela qual os eventos foram gravados
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    // Key da mensagem Kafka (ex: o orderId): decide a partição
    @Column(nullable = false)
    private String messageKey;

    // Nome lógico do evento (ex: orderCreated), vai no header __TypeId__
    @Column(nullable = false)
    private String type;

    // O evento já convertido para JSON
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    // null = ainda por enviar
    private Instant sentAt;
}
