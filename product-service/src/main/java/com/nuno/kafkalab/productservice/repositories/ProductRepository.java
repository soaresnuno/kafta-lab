package com.nuno.kafkalab.productservice.repositories;

import com.nuno.kafkalab.productservice.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}
