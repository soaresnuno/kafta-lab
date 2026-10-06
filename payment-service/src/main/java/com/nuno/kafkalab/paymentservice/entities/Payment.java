package com.nuno.kafkalab.paymentservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Um pagamento por encomenda: o unique impede cobrar a mesma encomenda duas vezes
    @Column(nullable = false, unique = true)
    private UUID orderId;

    // Quem paga (o dono da encomenda). Só ele pode ver este pagamento
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // Código ISO 4217 (ex: EUR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // Fornecedor que criou o pagamento (ex: "fake", "stripe"): cancelar e reembolsar vão sempre a este
    @Column(nullable = false)
    private String provider;

    // Id do pagamento no fornecedor; é por ele que os webhooks dizem de que pagamento falam
    private String providerPaymentId;

    // Onde o cliente conclui o pagamento (página do fornecedor)
    @Column(length = 1000)
    private String checkoutUrl;

    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;
}
