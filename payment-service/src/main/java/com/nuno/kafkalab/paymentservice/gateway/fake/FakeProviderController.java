package com.nuno.kafkalab.paymentservice.gateway.fake;

import com.nuno.kafkalab.paymentservice.config.PaymentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

// Simula a página de pagamento do fornecedor (no Stripe seria a página do Checkout).
// Pagar ou recusar aqui faz o "fornecedor" enviar por HTTP um webhook assinado ao payment-service,
// tal como um fornecedor real faria. Só existe com payments.fake.enabled=true
@RestController
@RequestMapping("/fake-provider/checkout")
@ConditionalOnProperty(name = "payments.fake.enabled", havingValue = "true")
public class FakeProviderController {

    private final RestClient restClient;
    private final PaymentProperties properties;
    private final JsonMapper jsonMapper;

    public FakeProviderController(RestClient.Builder restClientBuilder, PaymentProperties properties,
                                  JsonMapper jsonMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/{providerPaymentId}/pay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pay(@PathVariable String providerPaymentId) {
        sendWebhook(new FakeWebhookEvent(FakeWebhookEvent.SUCCEEDED, providerPaymentId, null));
    }

    @PostMapping("/{providerPaymentId}/decline")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void decline(@PathVariable String providerPaymentId) {
        sendWebhook(new FakeWebhookEvent(FakeWebhookEvent.FAILED, providerPaymentId, "Card declined"));
    }

    private void sendWebhook(FakeWebhookEvent event) {
        String payload = jsonMapper.writeValueAsString(event);
        restClient.post()
                .uri(properties.fake().webhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header(FakeWebhookSignature.HEADER, FakeWebhookSignature.sign(payload, properties.fake().webhookSecret()))
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
