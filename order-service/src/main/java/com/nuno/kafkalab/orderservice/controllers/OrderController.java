package com.nuno.kafkalab.orderservice.controllers;

import com.nuno.kafkalab.orderservice.dtos.CreateOrderRequest;
import com.nuno.kafkalab.orderservice.responses.OrderResponse;
import com.nuno.kafkalab.orderservice.security.CurrentUser;
import com.nuno.kafkalab.orderservice.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Todos os endpoints exigem token e trabalham só com as encomendas de quem faz o pedido.
// @AuthenticationPrincipal Jwt: o token já validado pelo Spring Security
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    public List<OrderResponse> getMine(@AuthenticationPrincipal Jwt jwt) {
        return orderService.getMine(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return orderService.create(request, CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orderService.getById(id, CurrentUser.id(jwt));
    }

    // POST em vez de DELETE: a encomenda não desaparece, muda para CANCELLED
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orderService.cancel(id, CurrentUser.id(jwt));
    }
}
