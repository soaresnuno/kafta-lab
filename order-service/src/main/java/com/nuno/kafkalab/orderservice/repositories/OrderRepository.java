package com.nuno.kafkalab.orderservice.repositories;

import com.nuno.kafkalab.orderservice.entities.Order;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @EntityGraph(attributePaths = "items")
    List<Order> findAllByUserId(UUID userId);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    // O "exists" filtra as encomendas; o @EntityGraph carrega TODOS os items de cada uma
    // (um "join ... where" no fetch traria só os items filtrados para dentro da entidade)
    @EntityGraph(attributePaths = "items")
    @Query("select o from Order o where exists (select 1 from OrderItem i where i.order = o and i.storeId = :storeId)")
    List<Order> findAllByStoreId(@Param("storeId") UUID storeId);
}
