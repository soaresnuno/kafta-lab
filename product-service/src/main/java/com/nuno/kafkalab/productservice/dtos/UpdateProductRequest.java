package com.nuno.kafkalab.productservice.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// PUT substitui o produto inteiro, por isso todos os campos obrigatórios continuam obrigatórios
public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull @Positive BigDecimal price,
        @NotNull @PositiveOrZero Integer stock
        ) {
}
