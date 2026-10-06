package com.nuno.kafkalab.paymentservice.controllers;

import com.nuno.kafkalab.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Recebe os webhooks dos fornecedores (ex: /payments/webhooks/fake, /payments/webhooks/stripe).
// É público (ver SecurityConfig): quem chama é o fornecedor, autenticado pela assinatura do webhook.
// O body vem como String porque a assinatura é calculada sobre os bytes exatos que o fornecedor enviou
@RestController
@RequestMapping("/payments/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final PaymentService paymentService;

    @PostMapping("/{provider}")
    public void receive(@PathVariable String provider, @RequestBody String payload,
                        @RequestHeader HttpHeaders headers) {
        Map<String, String> headerValues = new LinkedCaseInsensitiveMap<>();
        headerValues.putAll(headers.toSingleValueMap());
        paymentService.handleWebhook(provider, payload, headerValues);
    }
}
