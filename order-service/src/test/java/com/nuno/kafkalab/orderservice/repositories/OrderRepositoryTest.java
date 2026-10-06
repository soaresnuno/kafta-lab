package com.nuno.kafkalab.orderservice.repositories;

import com.nuno.kafkalab.orderservice.PostgresContainerConfiguration;
import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Só a camada JPA, contra um Postgres real num contentor.
// Cada teste corre numa transação que é desfeita no fim, por isso os testes não se afetam
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfiguration.class)
class OrderRepositoryTest {

    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void storeQueryFindsOrdersWithItemsOfThatStoreAndLoadsAllTheirItems() {
        Order mixed = save(order(CAROL, item(STORE_A), item(STORE_B)));
        save(order(CAROL, item(STORE_B)));
        entityManager.clear();

        List<Order> orders = orderRepository.findAllByStoreId(STORE_A);

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.getId()).isEqualTo(mixed.getId());
            // Os items da outra loja também vêm: quem filtra é a StoreOrderResponse
            assertThat(order.getItems()).hasSize(2);
        });
    }

    @Test
    void userQueriesOnlyReturnOrdersOfThatUser() {
        Order carolOrder = save(order(CAROL, item(STORE_A)));
        save(order(BOB, item(STORE_A)));

        assertThat(orderRepository.findAllByUserId(CAROL))
                .extracting(Order::getId)
                .containsExactly(carolOrder.getId());
        assertThat(orderRepository.findByIdAndUserId(carolOrder.getId(), CAROL)).isPresent();
        assertThat(orderRepository.findByIdAndUserId(carolOrder.getId(), BOB)).isEmpty();
    }

    private Order save(Order order) {
        return orderRepository.saveAndFlush(order);
    }

    private static Order order(UUID userId, OrderItem... items) {
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.CONFIRMED);
        for (OrderItem item : items) {
            order.addItem(item);
        }
        return order;
    }

    private static OrderItem item(UUID storeId) {
        OrderItem item = new OrderItem();
        item.setProductId(UUID.randomUUID());
        item.setStoreId(storeId);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));
        return item;
    }
}
