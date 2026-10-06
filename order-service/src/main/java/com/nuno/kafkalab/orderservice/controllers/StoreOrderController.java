package com.nuno.kafkalab.orderservice.controllers;

import com.nuno.kafkalab.orderservice.responses.StoreOrderResponse;
import com.nuno.kafkalab.orderservice.security.CurrentUser;
import com.nuno.kafkalab.orderservice.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Encomendas vistas por uma loja. Só o dono da loja as pode ver (ver OrderService.getByStore)
@RestController
@RequestMapping("/stores/{storeId}/orders")
@RequiredArgsConstructor
public class StoreOrderController {
    private final OrderService orderService;

    @GetMapping
    public List<StoreOrderResponse> getAll(@PathVariable UUID storeId, @AuthenticationPrincipal Jwt jwt) {
        return orderService.getByStore(storeId, CurrentUser.id(jwt));
    }
}
