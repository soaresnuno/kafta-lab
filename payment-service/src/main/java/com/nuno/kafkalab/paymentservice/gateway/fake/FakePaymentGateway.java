package com.nuno.kafkalab.paymentservice.gateway.fake;

import com.nuno.kafkalab.paymentservice.config.PaymentProperties;
import com.nuno.kafkalab.paymentservice.exceptions.InvalidWebhookException;
import com.nuno.kafkalab.paymentservice.gateway.PaymentGateway;
import com.nuno.kafkalab.paymentservice.gateway.PaymentRequest;
import com.nuno.kafkalab.paymentservice.gateway.ProviderPayment;
import com.nuno.kafkalab.paymentservice.gateway.ProviderUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

// Adapter de um fornecedor simulado, para desenvolvimento e testes. Comporta-se como um real:
// o pagamento fica por concluir, o cliente paga numa "página" (FakeProviderController)
// e o resultado chega por webhook assinado
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.fake.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FakePaymentGateway implements PaymentGateway {

    public static final String NAME = "fake";

    private final PaymentProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProviderPayment createPayment(PaymentRequest request) {
        String providerPaymentId = "fake_" + UUID.randomUUID();
        log.info("Fake provider: payment {} created for order {} ({} {})",
                providerPaymentId, request.orderId(), request.amount(), request.currency());
        return new ProviderPayment(providerPaymentId, properties.fake().checkoutBaseUrl() + "/" + providerPaymentId);
    }

    @Override
    public void cancel(String providerPaymentId) {
        log.info("Fake provider: payment {} cancelled", providerPaymentId);
    }

    @Override
    public void refund(String providerPaymentId, BigDecimal amount, String currency) {
        log.info("Fake provider: payment {} refunded ({} {})", providerPaymentId, amount, currency);
    }

    @Override
    public ProviderUpdate parseWebhook(String payload, Map<String, String> headers) {
        String signature = headers.get(FakeWebhookSignature.HEADER);
        if (!FakeWebhookSignature.isValid(payload, signature, properties.fake().webhookSecret())) {
            throw new InvalidWebhookException("Invalid signature");
        }

        FakeWebhookEvent event;
        try {
            event = jsonMapper.readValue(payload, FakeWebhookEvent.class);
        } catch (JacksonException e) {
            throw new InvalidWebhookException("Malformed payload");
        }

        return switch (event.type()) {
            case FakeWebhookEvent.SUCCEEDED ->
                    new ProviderUpdate(event.paymentId(), ProviderUpdate.Outcome.SUCCEEDED, null);
            case FakeWebhookEvent.FAILED ->
                    new ProviderUpdate(event.paymentId(), ProviderUpdate.Outcome.FAILED, event.failureMessage());
            default -> throw new InvalidWebhookException("Unsupported event type " + event.type());
        };
    }
}
