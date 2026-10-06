package com.nuno.kafkalab.storeservice.repositories;

import com.nuno.kafkalab.storeservice.entities.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findAllByOwnerId(UUID ownerId);
}
