package com.nuno.kafkalab.productservice.services;

import com.nuno.kafkalab.outbox.EventPublisher;
import com.nuno.kafkalab.productservice.config.KafkaConfig;
import com.nuno.kafkalab.productservice.entities.Product;
import com.nuno.kafkalab.productservice.entities.ReservationStatus;
import com.nuno.kafkalab.productservice.entities.ReservedItem;
import com.nuno.kafkalab.productservice.entities.StockReservation;
import com.nuno.kafkalab.productservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.productservice.events.OrderCreatedEvent;
import com.nuno.kafkalab.productservice.events.StockRejectedEvent;
import com.nuno.kafkalab.productservice.events.StockReservedEvent;
import com.nuno.kafkalab.productservice.repositories.ProductRepository;
import com.nuno.kafkalab.productservice.repositories.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private EventPublisher eventPublisher;
    @InjectMocks
    private StockReservationService service;

    @Captor
    private ArgumentCaptor<StockReservation> reservationCaptor;
    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    @Test
    void reservesStockAndPublishesStockReserved() {
        Product keyboard = product(5, true);
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(List.of(keyboard));

        service.reserve(orderCreated(item(keyboard, 2)));

        assertThat(keyboard.getStock()).isEqualTo(3);

        verify(reservationRepository).save(reservationCaptor.capture());
        StockReservation reservation = reservationCaptor.getValue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(keyboard.getId());
            assertThat(item.getQuantity()).isEqualTo(2);
        });

        verify(eventPublisher).publish(eq(KafkaConfig.STOCK_EVENTS), eq(ORDER_ID), eq(StockReservedEvent.TYPE), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new StockReservedEvent(ORDER_ID,
                List.of(new StockReservedEvent.Item(keyboard.getId(), STORE_ID, keyboard.getPrice()))));
    }

    @Test
    void sumsQuantitiesOfTheSameProduct() {
        Product keyboard = product(5, true);
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(List.of(keyboard));

        service.reserve(orderCreated(item(keyboard, 2), item(keyboard, 3)));

        assertThat(keyboard.getStock()).isZero();
        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getItems())
                .singleElement()
                .extracting(ReservedItem::getQuantity)
                .isEqualTo(5);
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() {
        Product keyboard = product(3, true);
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(List.of(keyboard));

        service.reserve(orderCreated(item(keyboard, 5)));

        assertThat(keyboard.getStock()).isEqualTo(3);
        assertRejectedWith("Insufficient stock");
    }

    @Test
    void rejectsOrderWhenProductDoesNotExist() {
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(List.of());

        service.reserve(orderCreated(new OrderCreatedEvent.Item(UUID.randomUUID(), 1)));

        assertRejectedWith("not found");
    }

    @Test
    void rejectsOrderWhenProductIsInactive() {
        Product keyboard = product(5, false);
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(List.of(keyboard));

        service.reserve(orderCreated(item(keyboard, 1)));

        assertThat(keyboard.getStock()).isEqualTo(5);
        assertRejectedWith("no longer available");
    }

    @Test
    void ignoresDuplicateOrderCreated() {
        when(reservationRepository.existsById(ORDER_ID)).thenReturn(true);

        service.reserve(orderCreated(new OrderCreatedEvent.Item(UUID.randomUUID(), 1)));

        verifyNoInteractions(productRepository, eventPublisher);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void releaseReturnsTheReservedStock() {
        Product keyboard = product(3, true);
        StockReservation reservation = reservation(ReservationStatus.RESERVED);
        reservation.getItems().add(new ReservedItem(keyboard.getId(), 2));
        when(reservationRepository.findById(ORDER_ID)).thenReturn(Optional.of(reservation));
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(List.of(keyboard));

        service.release(new OrderCancelledEvent(ORDER_ID));

        assertThat(keyboard.getStock()).isEqualTo(5);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void releaseIgnoresReservationThatIsNotReserved() {
        StockReservation reservation = reservation(ReservationStatus.REJECTED);
        when(reservationRepository.findById(ORDER_ID)).thenReturn(Optional.of(reservation));

        service.release(new OrderCancelledEvent(ORDER_ID));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        verifyNoInteractions(productRepository);
    }

    @Test
    void releaseIgnoresUnknownOrder() {
        when(reservationRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        service.release(new OrderCancelledEvent(ORDER_ID));

        verifyNoInteractions(productRepository);
    }

    private void assertRejectedWith(String reason) {
        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservationCaptor.getValue().getItems()).isEmpty();

        verify(eventPublisher).publish(eq(KafkaConfig.STOCK_EVENTS), eq(ORDER_ID), eq(StockRejectedEvent.TYPE), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOfSatisfying(StockRejectedEvent.class,
                event -> assertThat(event.reason()).contains(reason));
    }

    private static Product product(int stock, boolean active) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setStoreId(STORE_ID);
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(stock);
        product.setActive(active);
        return product;
    }

    private static StockReservation reservation(ReservationStatus status) {
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(ORDER_ID);
        reservation.setStatus(status);
        return reservation;
    }

    private static OrderCreatedEvent orderCreated(OrderCreatedEvent.Item... items) {
        return new OrderCreatedEvent(ORDER_ID, List.of(items));
    }

    private static OrderCreatedEvent.Item item(Product product, int quantity) {
        return new OrderCreatedEvent.Item(product.getId(), quantity);
    }
}
