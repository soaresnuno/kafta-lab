package com.nuno.kafkalab.productservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Cópia local das lojas, alimentada pelos eventos de store-events (event-carried state transfer).
// Permite validar a loja de um produto sem chamar o store-service, mesmo que ele esteja em baixo.
// Guarda só o que este serviço precisa
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
    private boolean active;
}
