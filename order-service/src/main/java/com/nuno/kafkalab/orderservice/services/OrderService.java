package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.config.KafkaConfig;
import com.nuno.kafkalab.orderservice.dtos.CreateOrderRequest;
import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import com.nuno.kafkalab.orderservice.entities.StoreReplica;
import com.nuno.kafkalab.orderservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.orderservice.events.OrderCreatedEvent;
import com.nuno.kafkalab.orderservice.events.StockRejectedEvent;
import com.nuno.kafkalab.orderservice.events.StockReservedEvent;
import com.nuno.kafkalab.orderservice.exceptions.InvalidOrderStatusException;
import com.nuno.kafkalab.orderservice.exceptions.OrderNotFoundException;
import com.nuno.kafkalab.orderservice.exceptions.StoreAccessDeniedException;
import com.nuno.kafkalab.orderservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.orderservice.messaging.EventPublisher;
import com.nuno.kafkalab.orderservice.repositories.OrderRepository;
import com.nuno.kafkalab.orderservice.repositories.StoreReplicaRepository;
import com.nuno.kafkalab.orderservice.responses.OrderResponse;
import com.nuno.kafkalab.orderservice.responses.StoreOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StoreReplicaRepository storeRepository;
    private final EventPublisher eventPublisher;

    // Só as encomendas do próprio utilizador
    @Transactional(readOnly = true)
    public List<OrderResponse> getMine(UUID userId) {
        return orderRepository.findAllByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, UUID userId) {
        Order order = new Order();
        order.setUserId(userId);

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
        eventPublisher.publish(KafkaConfig.ORDER_EVENTS, saved.getId(), OrderCreatedEvent.TYPE, event);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id, UUID userId) {
        return OrderResponse.from(findOwnOrThrow(id, userId));
    }

    // Encomendas com items desta loja, só para o dono da loja. Cada loja só vê os seus items
    @Transactional(readOnly = true)
    public List<StoreOrderResponse> getByStore(UUID storeId, UUID userId) {
        StoreReplica store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
        if (!store.getOwnerId().equals(userId)) {
            throw new StoreAccessDeniedException(storeId);
        }

        return orderRepository.findAllByStoreId(storeId).stream()
                .map(order -> StoreOrderResponse.from(order, storeId))
                .toList();
    }

    // Em vez de apagar, cancela: fica o histórico e o product-service devolve o stock
    @Transactional
    public OrderResponse cancel(UUID id, UUID userId) {
        Order order = findOwnOrThrow(id, userId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStatusException(id, order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publish(KafkaConfig.ORDER_EVENTS, id, OrderCancelledEvent.TYPE, new OrderCancelledEvent(id));

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
        Map<UUID, StockReservedEvent.Item> reserved = event.items().stream()
                .collect(Collectors.toMap(StockReservedEvent.Item::productId, Function.identity()));
        for (OrderItem item : order.getItems()) {
            StockReservedEvent.Item reservedItem = reserved.get(item.getProductId());
            item.setStoreId(reservedItem.storeId());
            item.setUnitPrice(reservedItem.unitPrice());
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

    // A encomenda de outra pessoa responde 404, tal como uma que não existe: não revela que existe
    private Order findOwnOrThrow(UUID id, UUID userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
