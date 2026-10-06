package com.nuno.kafkalab.productservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// @Embeddable: não tem id nem tabela própria; vive dentro da StockReservation
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservedItem {
    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;
}
