package com.nuno.kafkalab.orderservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Cópia local das lojas, alimentada pelos eventos de store-events (event-carried state transfer).
// O order-service só precisa de saber quem é o dono, para mostrar as encomendas da loja apenas a ele
@Entity
@Table(name = "store_replicas")
@Getter
@Setter
@NoArgsConstructor
public class StoreReplica {
    // O id é o storeId que vem no evento, não é gerado aqui
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    // Uma loja inativa continua a ver o histórico de encomendas
    @Column(nullable = false)
    private boolean active;
}
