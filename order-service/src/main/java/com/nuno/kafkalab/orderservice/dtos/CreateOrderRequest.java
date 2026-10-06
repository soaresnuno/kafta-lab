package com.nuno.kafkalab.orderservice.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// Sem userId: quem encomenda é o utilizador do token, não o que vier no body
public record CreateOrderRequest(
        @NotEmpty List<@Valid CreateOrderItemRequest> items
        ) {
}
