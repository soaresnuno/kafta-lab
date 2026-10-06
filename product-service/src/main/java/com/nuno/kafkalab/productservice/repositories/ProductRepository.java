package com.nuno.kafkalab.productservice.repositories;

import com.nuno.kafkalab.productservice.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // SELECT ... FOR UPDATE: bloqueia estas linhas até ao fim da transação,
    // para duas reservas (ou um PUT) ao mesmo tempo não venderem o mesmo stock.
    // O "order by" faz com que as transações bloqueiem sempre pela mesma ordem e evita deadlocks
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
