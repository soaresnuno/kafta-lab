package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.config.KafkaConfig;
import com.nuno.kafkalab.orderservice.config.OrderProperties;
import com.nuno.kafkalab.orderservice.dtos.CreateOrderRequest;
import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import com.nuno.kafkalab.orderservice.entities.StoreReplica;
import com.nuno.kafkalab.orderservice.events.CancelPaymentCommand;
import com.nuno.kafkalab.orderservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.orderservice.events.OrderCreatedEvent;
import com.nuno.kafkalab.orderservice.events.PaymentFailedEvent;
import com.nuno.kafkalab.orderservice.events.PaymentSucceededEvent;
import com.nuno.kafkalab.orderservice.events.RequestPaymentCommand;
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

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Ciclo de vida de uma encomenda:
// PENDING -> (stock reservado) AWAITING_PAYMENT -> (pago) CONFIRMED
//         -> (sem stock) REJECTED      -> (pagamento recusado) PAYMENT_FAILED
// PENDING, AWAITING_PAYMENT e CONFIRMED podem ser canceladas (CANCELLED)
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED);

    private final OrderRepository orderRepository;
    private final StoreReplicaRepository storeRepository;
    private final EventPublisher eventPublisher;
    private final OrderProperties properties;

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

    // Em vez de apagar, cancela: fica o histórico, o product-service devolve o stock
    // e o payment-service cancela ou reembolsa o pagamento
    @Transactional
    public OrderResponse cancel(UUID id, UUID userId) {
        Order order = findOwnOrThrow(id, userId);
        if (!CANCELLABLE.contains(order.getStatus())) {
            throw new InvalidOrderStatusException(id, order.getStatus());
        }

        cancelOrder(order, null);
        return OrderResponse.from(order);
    }

    // Chamado pelo StockEventsListener. Com o stock garantido, pede o pagamento.
    // Só uma encomenda PENDING muda de estado, por isso um evento repetido é ignorado (idempotente)
    @Transactional
    public void stockReserved(StockReservedEvent event) {
        Optional<Order> pending = findInStatus(event.orderId(), OrderStatus.PENDING);
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
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setAwaitingPaymentSince(Instant.now());

        // O total só se sabe agora, com os preços que o product-service mandou
        eventPublisher.publish(KafkaConfig.PAYMENT_COMMANDS, order.getId(), RequestPaymentCommand.TYPE,
                new RequestPaymentCommand(order.getId(), order.getUserId(), order.getTotal(), properties.currency()));
        log.info("Stock reserved for order {}, payment requested", order.getId());
    }

    @Transactional
    public void stockRejected(StockRejectedEvent event) {
        findInStatus(event.orderId(), OrderStatus.PENDING).ifPresent(order -> {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason(event.reason());
            log.info("Order {} rejected: {}", order.getId(), event.reason());
        });
    }

    // Chamado pelo PaymentEventsListener
    @Transactional
    public void paymentSucceeded(PaymentSucceededEvent event) {
        findInStatus(event.orderId(), OrderStatus.AWAITING_PAYMENT).ifPresent(order -> {
            order.setStatus(OrderStatus.CONFIRMED);
            log.info("Order {} paid and confirmed", order.getId());
        });
    }

    // Pagamento recusado: a encomenda não avança e o stock reservado é devolvido
    @Transactional
    public void paymentFailed(PaymentFailedEvent event) {
        findInStatus(event.orderId(), OrderStatus.AWAITING_PAYMENT).ifPresent(order -> {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setRejectionReason(event.reason());
            eventPublisher.publish(KafkaConfig.ORDER_EVENTS, order.getId(), OrderCancelledEvent.TYPE,
                    new OrderCancelledEvent(order.getId()));
            log.info("Payment failed for order {}: {}", order.getId(), event.reason());
        });
    }

    // Chamado pelo UnpaidOrdersJob. Uma encomenda por pagar há demasiado tempo é cancelada,
    // para o stock não ficar reservado para sempre
    @Transactional
    public void expireUnpaidOrders() {
        Instant cutoff = Instant.now().minus(properties.paymentTimeout());
        for (Order order : orderRepository.findAllByStatusAndAwaitingPaymentSinceBefore(OrderStatus.AWAITING_PAYMENT, cutoff)) {
            cancelOrder(order, "Payment not completed in time");
            log.info("Order {} cancelled: payment not completed in time", order.getId());
        }
    }

    // O product-service devolve o stock com o OrderCancelled. Se o pagamento já tinha sido pedido,
    // o payment-service também tem de saber, para o cancelar ou reembolsar
    private void cancelOrder(Order order, String reason) {
        boolean paymentRequested = order.getStatus() == OrderStatus.AWAITING_PAYMENT
                || order.getStatus() == OrderStatus.CONFIRMED;

        order.setStatus(OrderStatus.CANCELLED);
        order.setRejectionReason(reason);
        eventPublisher.publish(KafkaConfig.ORDER_EVENTS, order.getId(), OrderCancelledEvent.TYPE,
                new OrderCancelledEvent(order.getId()));
        if (paymentRequested) {
            eventPublisher.publish(KafkaConfig.PAYMENT_COMMANDS, order.getId(), CancelPaymentCommand.TYPE,
                    new CancelPaymentCommand(order.getId()));
        }
    }

    // Um evento só muda a encomenda se ela estiver no estado esperado: repetidos ou atrasados são ignorados
    private Optional<Order> findInStatus(UUID id, OrderStatus expected) {
        Optional<Order> order = orderRepository.findById(id);
        if (order.isEmpty()) {
            log.warn("Order {} not found, ignoring event", id);
            return Optional.empty();
        }
        if (order.get().getStatus() != expected) {
            log.info("Order {} is {}, ignoring event", id, order.get().getStatus());
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
