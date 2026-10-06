package com.nuno.kafkalab.paymentservice.controllers;

import com.nuno.kafkalab.paymentservice.responses.PaymentResponse;
import com.nuno.kafkalab.paymentservice.security.CurrentUser;
import com.nuno.kafkalab.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// O cliente consulta aqui o pagamento da sua encomenda, incluindo o checkoutUrl onde vai pagar
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @GetMapping("/orders/{orderId}")
    public PaymentResponse getForOrder(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getForOrder(orderId, CurrentUser.id(jwt));
    }
}
