package com.nuno.kafkalab.productservice.repositories;

import com.nuno.kafkalab.productservice.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByActiveTrue();

    List<Product> findAllByStoreId(UUID storeId);

    // Um produto só pode ser alterado pela loja dona e enquanto estiver ativo
    Optional<Product> findByIdAndStoreIdAndActiveTrue(UUID id, UUID storeId);

    // SELECT ... FOR UPDATE: bloqueia estas linhas até ao fim da transação,
    // para duas reservas (ou um PUT) ao mesmo tempo não venderem o mesmo stock.
    // O "order by" faz com que as transações bloqueiem sempre pela mesma ordem e evita deadlocks
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    // Um único UPDATE para todos os produtos da loja, em vez de carregar e gravar um a um
    @Modifying
    @Query("update Product p set p.active = false where p.storeId = :storeId and p.active = true")
    int deactivateAllByStoreId(@Param("storeId") UUID storeId);
}
