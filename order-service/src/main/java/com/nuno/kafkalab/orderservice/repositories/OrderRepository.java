package com.nuno.kafkalab.orderservice.repositories;

import com.nuno.kafkalab.orderservice.entities.Order;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@NullMarked
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Override
    @EntityGraph(attributePaths = "items")
    List<Order> findAll();
}
