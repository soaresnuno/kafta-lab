package com.nuno.kafkalab.productservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Guarda o que foi reservado para cada encomenda. Serve para:
// - ignorar um OrderCreated repetido (o Kafka pode entregar a mesma mensagem duas vezes)
// - saber exatamente quanto stock devolver quando a encomenda é cancelada
@Entity
@Table(name = "stock_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation {
    // O id é o orderId que vem no evento, não é gerado aqui
    @Id
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @ElementCollection
    @CollectionTable(name = "stock_reservation_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<ReservedItem> items = new ArrayList<>();
}
