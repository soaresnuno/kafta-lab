package com.nuno.kafkalab.orderservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name= "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    // STRING guarda "PENDING" na BD em vez do índice (0, 1, ...), que partia se a ordem do enum mudasse
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    // Preenchido só quando o product-service rejeita a encomenda
    private String rejectionReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Mantém os dois lados da relação sincronizados:
    // a lista da Order E o campo order do OrderItem
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
