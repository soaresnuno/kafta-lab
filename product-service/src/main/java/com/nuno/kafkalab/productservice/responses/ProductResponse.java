package com.nuno.kafkalab.productservice.responses;

import com.nuno.kafkalab.productservice.entities.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID storeId,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        boolean active
) {
    public static ProductResponse from (Product product) {
        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isActive()
        );
    }
}
