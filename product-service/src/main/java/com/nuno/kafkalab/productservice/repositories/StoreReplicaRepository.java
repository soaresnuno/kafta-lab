package com.nuno.kafkalab.productservice.repositories;

import com.nuno.kafkalab.productservice.entities.StoreReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreReplicaRepository extends JpaRepository<StoreReplica, UUID> {
}
