package com.nuno.kafkalab.productservice.services;

import com.nuno.kafkalab.productservice.config.KafkaConfig;
import com.nuno.kafkalab.productservice.entities.Product;
import com.nuno.kafkalab.productservice.entities.ReservationStatus;
import com.nuno.kafkalab.productservice.entities.ReservedItem;
import com.nuno.kafkalab.productservice.entities.StockReservation;
import com.nuno.kafkalab.productservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.productservice.events.OrderCreatedEvent;
import com.nuno.kafkalab.productservice.events.StockRejectedEvent;
import com.nuno.kafkalab.productservice.events.StockReservedEvent;
import com.nuno.kafkalab.productservice.messaging.EventPublisher;
import com.nuno.kafkalab.productservice.repositories.ProductRepository;
import com.nuno.kafkalab.productservice.repositories.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;
    private final EventPublisher eventPublisher;

    // Tudo numa transação: ou o stock é retirado e a reserva gravada, ou nada acontece
    @Transactional
    public void reserve(OrderCreatedEvent event) {
        UUID orderId = event.orderId();

        // O Kafka garante "at least once": a mesma mensagem pode chegar duas vezes
        if (reservationRepository.existsById(orderId)) {
            log.info("Order {} already processed, ignoring duplicate OrderCreated", orderId);
            return;
        }

        // Soma as quantidades: a encomenda pode ter o mesmo produto em duas linhas
        Map<UUID, Integer> requested = event.items().stream()
                .collect(Collectors.toMap(
                        OrderCreatedEvent.Item::productId,
                        OrderCreatedEvent.Item::quantity,
                        Integer::sum,
                        LinkedHashMap::new));
        Map<UUID, Product> products = lockProducts(requested.keySet());

        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);

        String reason = findRejectionReason(requested, products);
        if (reason != null) {
            reservation.setStatus(ReservationStatus.REJECTED);
            reservationRepository.save(reservation);
            eventPublisher.publish(KafkaConfig.STOCK_EVENTS, orderId, StockRejectedEvent.TYPE, new StockRejectedEvent(orderId, reason));
            log.info("Order {} rejected: {}", orderId, reason);
            return;
        }

        List<StockReservedEvent.Item> reservedItems = new ArrayList<>();
        requested.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            product.setStock(product.getStock() - quantity);
            reservation.getItems().add(new ReservedItem(productId, quantity));
            reservedItems.add(new StockReservedEvent.Item(productId, product.getStoreId(), product.getPrice()));
        });
        reservation.setStatus(ReservationStatus.RESERVED);
        reservationRepository.save(reservation);

        eventPublisher.publish(KafkaConfig.STOCK_EVENTS, orderId, StockReservedEvent.TYPE, new StockReservedEvent(orderId, reservedItems));
        log.info("Stock reserved for order {}", orderId);
    }

    @Transactional
    public void release(OrderCancelledEvent event) {
        Optional<StockReservation> found = reservationRepository.findById(event.orderId());

        // Só há stock para devolver se foi reservado. REJECTED não retirou nada;
        // RELEASED já foi devolvido (mensagem repetida)
        if (found.isEmpty() || found.get().getStatus() != ReservationStatus.RESERVED) {
            log.info("Nothing to release for order {}", event.orderId());
            return;
        }

        StockReservation reservation = found.get();
        Map<UUID, Product> products = lockProducts(
                reservation.getItems().stream().map(ReservedItem::getProductId).toList());

        for (ReservedItem item : reservation.getItems()) {
            Product product = products.get(item.getProductId());
            if (product == null) {
                log.warn("Product {} no longer exists, cannot return {} units", item.getProductId(), item.getQuantity());
                continue;
            }
            product.setStock(product.getStock() + item.getQuantity());
        }
        reservation.setStatus(ReservationStatus.RELEASED);
        log.info("Stock released for order {}", event.orderId());
    }

    private Map<UUID, Product> lockProducts(Collection<UUID> ids) {
        return productRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    // Devolve o motivo da rejeição, ou null se todos os produtos existem, estão ativos e têm stock
    private String findRejectionReason(Map<UUID, Integer> requested, Map<UUID, Product> products) {
        for (var entry : requested.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null) {
                return "Product " + entry.getKey() + " not found";
            }
            if (!product.isActive()) {
                return "Product " + entry.getKey() + " is no longer available";
            }
            if (product.getStock() < entry.getValue()) {
                return "Insufficient stock for product " + entry.getKey()
                        + " (requested " + entry.getValue() + ", available " + product.getStock() + ")";
            }
        }
        return null;
    }
}
