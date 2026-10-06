package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.config.KafkaConfig;
import com.nuno.kafkalab.orderservice.config.OrderProperties;
import com.nuno.kafkalab.orderservice.dtos.CreateOrderItemRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StoreReplicaRepository storeRepository;
    @Mock
    private EventPublisher eventPublisher;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, storeRepository, eventPublisher,
                new OrderProperties("EUR", Duration.ofMinutes(15)));
    }

    @Test
    void createSavesPendingOrderOfTheUserAndPublishesOrderCreated() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(ORDER_ID);
            return order;
        });

        OrderResponse response = service.create(
                new CreateOrderRequest(List.of(new CreateOrderItemRequest(PRODUCT_ID, 2))), CAROL);

        assertThat(response.id()).isEqualTo(ORDER_ID);
        assertThat(response.userId()).isEqualTo(CAROL);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.total()).isNull();
        verify(eventPublisher).publish(KafkaConfig.ORDER_EVENTS, ORDER_ID, OrderCreatedEvent.TYPE,
                new OrderCreatedEvent(ORDER_ID, List.of(new OrderCreatedEvent.Item(PRODUCT_ID, 2))));
    }

    @Test
    void stockReservedSetsPricesAndRequestsThePayment() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.stockReserved(stockReserved());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.getAwaitingPaymentSince()).isNotNull();
        OrderItem item = order.getItems().getFirst();
        assertThat(item.getStoreId()).isEqualTo(STORE_ID);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("100.00");
        verify(eventPublisher).publish(KafkaConfig.PAYMENT_COMMANDS, ORDER_ID, RequestPaymentCommand.TYPE,
                new RequestPaymentCommand(ORDER_ID, CAROL, new BigDecimal("200.00"), "EUR"));
    }

    @Test
    void stockReservedIsIgnoredWhenTheOrderIsNoLongerPending() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.stockReserved(stockReserved());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getItems().getFirst().getUnitPrice()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void stockRejectedStoresTheReason() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.stockRejected(new StockRejectedEvent(ORDER_ID, "Insufficient stock"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Insufficient stock");
    }

    @Test
    void paymentSucceededConfirmsTheOrder() {
        Order order = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.paymentSucceeded(new PaymentSucceededEvent(ORDER_ID, UUID.randomUUID()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentSucceededIsIgnoredWhenTheOrderWasCancelled() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.paymentSucceeded(new PaymentSucceededEvent(ORDER_ID, UUID.randomUUID()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paymentFailedReleasesTheStock() {
        Order order = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.paymentFailed(new PaymentFailedEvent(ORDER_ID, "Card declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getRejectionReason()).isEqualTo("Card declined");
        verify(eventPublisher).publish(KafkaConfig.ORDER_EVENTS, ORDER_ID, OrderCancelledEvent.TYPE,
                new OrderCancelledEvent(ORDER_ID));
        verify(eventPublisher, never()).publish(eq(KafkaConfig.PAYMENT_COMMANDS), any(), any(), any());
    }

    @Test
    void cancellingAPaidOrderReleasesTheStockAndCancelsThePayment() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, CAROL)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));

        OrderResponse response = service.cancel(ORDER_ID, CAROL);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publish(KafkaConfig.ORDER_EVENTS, ORDER_ID, OrderCancelledEvent.TYPE,
                new OrderCancelledEvent(ORDER_ID));
        verify(eventPublisher).publish(KafkaConfig.PAYMENT_COMMANDS, ORDER_ID, CancelPaymentCommand.TYPE,
                new CancelPaymentCommand(ORDER_ID));
    }

    @Test
    void cancellingAPendingOrderDoesNotTouchThePayment() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, CAROL)).thenReturn(Optional.of(order(OrderStatus.PENDING)));

        service.cancel(ORDER_ID, CAROL);

        verify(eventPublisher).publish(KafkaConfig.ORDER_EVENTS, ORDER_ID, OrderCancelledEvent.TYPE,
                new OrderCancelledEvent(ORDER_ID));
        verify(eventPublisher, never()).publish(eq(KafkaConfig.PAYMENT_COMMANDS), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"REJECTED", "PAYMENT_FAILED", "CANCELLED"})
    void finishedOrderCannotBeCancelled(OrderStatus status) {
        when(orderRepository.findByIdAndUserId(ORDER_ID, CAROL)).thenReturn(Optional.of(order(status)));

        assertThatThrownBy(() -> service.cancel(ORDER_ID, CAROL))
                .isInstanceOf(InvalidOrderStatusException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unpaidOrdersAreCancelledAfterTheTimeout() {
        Order unpaid = order(OrderStatus.AWAITING_PAYMENT);
        unpaid.setAwaitingPaymentSince(Instant.now().minus(Duration.ofMinutes(20)));
        when(orderRepository.findAllByStatusAndAwaitingPaymentSinceBefore(eq(OrderStatus.AWAITING_PAYMENT), any()))
                .thenReturn(List.of(unpaid));

        service.expireUnpaidOrders();

        assertThat(unpaid.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(unpaid.getRejectionReason()).isEqualTo("Payment not completed in time");
        verify(eventPublisher).publish(KafkaConfig.ORDER_EVENTS, ORDER_ID, OrderCancelledEvent.TYPE,
                new OrderCancelledEvent(ORDER_ID));
        verify(eventPublisher).publish(KafkaConfig.PAYMENT_COMMANDS, ORDER_ID, CancelPaymentCommand.TYPE,
                new CancelPaymentCommand(ORDER_ID));
    }

    @Test
    void orderOfAnotherUserIsNotFound() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, BOB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(ORDER_ID, BOB))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void storeOrdersAreOnlyVisibleToTheStoreOwner() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store()));

        assertThatThrownBy(() -> service.getByStore(STORE_ID, BOB))
                .isInstanceOf(StoreAccessDeniedException.class);
        verify(orderRepository, never()).findAllByStoreId(any());
    }

    @Test
    void storeOrdersOfUnknownStoreAreNotFound() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByStore(STORE_ID, ALICE))
                .isInstanceOf(StoreNotFoundException.class);
    }

    private static Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUserId(CAROL);
        order.setStatus(status);

        OrderItem item = new OrderItem();
        item.setProductId(PRODUCT_ID);
        item.setQuantity(2);
        order.addItem(item);
        return order;
    }

    private static StoreReplica store() {
        StoreReplica store = new StoreReplica();
        store.setId(STORE_ID);
        store.setOwnerId(ALICE);
        store.setActive(true);
        return store;
    }

    private static StockReservedEvent stockReserved() {
        return new StockReservedEvent(ORDER_ID,
                List.of(new StockReservedEvent.Item(PRODUCT_ID, STORE_ID, new BigDecimal("100.00"))));
    }
}
