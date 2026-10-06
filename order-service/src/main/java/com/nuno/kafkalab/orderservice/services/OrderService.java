package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.config.KafkaConfig;
import com.nuno.kafkalab.orderservice.dtos.CreateOrderRequest;
import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import com.nuno.kafkalab.orderservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.orderservice.events.OrderCreatedEvent;
import com.nuno.kafkalab.orderservice.events.StockRejectedEvent;
import com.nuno.kafkalab.orderservice.events.StockReservedEvent;
import com.nuno.kafkalab.orderservice.exceptions.InvalidOrderStatusException;
import com.nuno.kafkalab.orderservice.exceptions.OrderNotFoundException;
import com.nuno.kafkalab.orderservice.messaging.EventPublisher;
import com.nuno.kafkalab.orderservice.repositories.OrderRepository;
import com.nuno.kafkalab.orderservice.responses.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.userId());

        for (var itemRequest : request.items()) {
            OrderItem item = new OrderItem();
            item.setProductId(itemRequest.productId());
            item.setQuantity(itemRequest.quantity());
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);

        // A encomenda fica PENDING; o product-service responde com StockReserved ou StockRejected
        var event = new OrderCreatedEvent(
                saved.getId(),
                saved.getItems().stream()
                        .map(item -> new OrderCreatedEvent.Item(item.getProductId(), item.getQuantity()))
                        .toList()
        );
        eventPublisher.publishAfterCommit(KafkaConfig.ORDER_EVENTS, saved.getId(), event);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return OrderResponse.from(findOrThrow(id));
    }

    // Em vez de apagar, cancela: fica o histórico e o product-service devolve o stock
    @Transactional
    public OrderResponse cancel(UUID id) {
        Order order = findOrThrow(id);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStatusException(id, order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishAfterCommit(KafkaConfig.ORDER_EVENTS, id, new OrderCancelledEvent(id));

        return OrderResponse.from(order);
    }

    // Chamado pelo StockEventsListener. Só uma encomenda PENDING muda de estado,
    // por isso receber o mesmo evento duas vezes não faz mal (idempotente)
    @Transactional
    public void confirm(StockReservedEvent event) {
        Optional<Order> pending = findPending(event.orderId());
        if (pending.isEmpty()) {
            return;
        }

        Order order = pending.get();
        Map<UUID, BigDecimal> prices = event.items().stream()
                .collect(Collectors.toMap(StockReservedEvent.Item::productId, StockReservedEvent.Item::unitPrice));
        for (OrderItem item : order.getItems()) {
            item.setUnitPrice(prices.get(item.getProductId()));
        }
        order.setStatus(OrderStatus.CONFIRMED);
        log.info("Order {} confirmed", order.getId());
    }

    @Transactional
    public void reject(StockRejectedEvent event) {
        findPending(event.orderId()).ifPresent(order -> {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason(event.reason());
            log.info("Order {} rejected: {}", order.getId(), event.reason());
        });
    }

    private Optional<Order> findPending(UUID id) {
        Optional<Order> order = orderRepository.findById(id);
        if (order.isEmpty()) {
            log.warn("Order {} not found, ignoring stock event", id);
            return Optional.empty();
        }
        if (order.get().getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is {}, ignoring stock event", id, order.get().getStatus());
            return Optional.empty();
        }
        return order;
    }

    private Order findOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
