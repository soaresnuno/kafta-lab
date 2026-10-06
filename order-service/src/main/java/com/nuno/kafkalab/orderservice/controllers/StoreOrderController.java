package com.nuno.kafkalab.orderservice.controllers;

import com.nuno.kafkalab.orderservice.responses.StoreOrderResponse;
import com.nuno.kafkalab.orderservice.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Encomendas vistas por uma loja. Por agora qualquer pessoa pode chamar este endpoint;
// garantir que é mesmo o dono da loja fica para o passo do Keycloak
@RestController
@RequestMapping("/stores/{storeId}/orders")
@RequiredArgsConstructor
public class StoreOrderController {
    private final OrderService orderService;

    @GetMapping
    public List<StoreOrderResponse> getAll(@PathVariable UUID storeId) {
        return orderService.getByStore(storeId);
    }
}
