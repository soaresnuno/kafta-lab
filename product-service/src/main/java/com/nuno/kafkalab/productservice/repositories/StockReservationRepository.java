package com.nuno.kafkalab.productservice.repositories;

import com.nuno.kafkalab.productservice.entities.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
}
