package com.nuno.kafkalab.orderservice.responses;

import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderResponseTest {

    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();

    @Test
    void totalIsNullWhileAnyItemHasNoPrice() {
        Order order = order(item(STORE_A, 2, "100.00"), item(null, 1, null));

        assertThat(OrderResponse.from(order).total()).isNull();
    }

    @Test
    void totalIsTheSumOfPriceTimesQuantity() {
        Order order = order(item(STORE_A, 2, "100.00"), item(STORE_B, 3, "20.00"));

        assertThat(OrderResponse.from(order).total()).isEqualByComparingTo("260.00");
    }

    @Test
    void storeOrderShowsOnlyTheItemsAndTotalOfThatStore() {
        Order order = order(item(STORE_A, 2, "100.00"), item(STORE_B, 3, "20.00"));

        StoreOrderResponse response = StoreOrderResponse.from(order, STORE_B);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.storeId()).isEqualTo(STORE_B);
            assertThat(item.quantity()).isEqualTo(3);
        });
        assertThat(response.total()).isEqualByComparingTo("60.00");
    }

    private static Order order(OrderItem... items) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setStatus(OrderStatus.CONFIRMED);
        for (OrderItem item : items) {
            order.addItem(item);
        }
        return order;
    }

    private static OrderItem item(UUID storeId, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductId(UUID.randomUUID());
        item.setStoreId(storeId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return item;
    }
}
