package com.nuno.kafkalab.orderservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
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

    // Motivo quando a encomenda não avança: sem stock, pagamento recusado ou pagamento fora do prazo
    private String rejectionReason;

    // Quando ficou à espera de pagamento. Serve para cancelar as que ficam por pagar demasiado tempo
    private Instant awaitingPaymentSince;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Mantém os dois lados da relação sincronizados:
    // a lista da Order E o campo order do OrderItem
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public BigDecimal getTotal() {
        return totalOf(items);
    }

    // Soma de preço × quantidade. É null enquanto algum item não tem preço,
    // ou seja, antes de o product-service reservar o stock e dizer os preços
    public static BigDecimal totalOf(List<OrderItem> items) {
        if (items.stream().anyMatch(item -> item.getUnitPrice() == null)) {
            return null;
        }
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
