package com.nuno.kafkalab.orderservice.repositories;

import com.nuno.kafkalab.orderservice.entities.StoreReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreReplicaRepository extends JpaRepository<StoreReplica, UUID> {
}
