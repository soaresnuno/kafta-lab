package com.nuno.kafkalab.productservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Loja dona do produto. Só o id: a loja em si vive no store-service
    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    // BigDecimal em vez de double: dinheiro precisa de valores exatos
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    // Soft delete: um produto "apagado" fica inativo, porque encomendas antigas apontam para ele.
    // Inativo = fora do catálogo e não pode ser encomendado
    @Column(nullable = false)
    private boolean active = true;
}
