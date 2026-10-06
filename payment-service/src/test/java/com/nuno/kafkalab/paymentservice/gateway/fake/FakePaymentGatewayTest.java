package com.nuno.kafkalab.paymentservice.gateway.fake;

import com.nuno.kafkalab.paymentservice.config.PaymentProperties;
import com.nuno.kafkalab.paymentservice.exceptions.InvalidWebhookException;
import com.nuno.kafkalab.paymentservice.gateway.PaymentRequest;
import com.nuno.kafkalab.paymentservice.gateway.ProviderPayment;
import com.nuno.kafkalab.paymentservice.gateway.ProviderUpdate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePaymentGatewayTest {

    private static final String SECRET = "test-secret";

    private final FakePaymentGateway gateway = new FakePaymentGateway(
            new PaymentProperties("fake", new PaymentProperties.Fake(true, "http://pay.test/checkout", "http://unused", SECRET)),
            JsonMapper.builder().build());

    @Test
    void createPaymentReturnsTheCheckoutUrl() {
        ProviderPayment payment = gateway.createPayment(
                new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("200.00"), "EUR"));

        assertThat(payment.providerPaymentId()).startsWith("fake_");
        assertThat(payment.checkoutUrl()).isEqualTo("http://pay.test/checkout/" + payment.providerPaymentId());
    }

    @Test
    void signedSuccessWebhookIsTranslatedToTheCommonFormat() {
        String payload = """
                {"type": "payment.succeeded", "paymentId": "fake_1"}
                """;

        ProviderUpdate update = gateway.parseWebhook(payload, signed(payload));

        assertThat(update).isEqualTo(new ProviderUpdate("fake_1", ProviderUpdate.Outcome.SUCCEEDED, null));
    }

    @Test
    void signedFailureWebhookKeepsTheReason() {
        String payload = """
                {"type": "payment.failed", "paymentId": "fake_1", "failureMessage": "Card declined"}
                """;

        ProviderUpdate update = gateway.parseWebhook(payload, signed(payload));

        assertThat(update).isEqualTo(new ProviderUpdate("fake_1", ProviderUpdate.Outcome.FAILED, "Card declined"));
    }

    @Test
    void webhookSignedWithAnotherSecretIsRejected() {
        String payload = """
                {"type": "payment.succeeded", "paymentId": "fake_1"}
                """;
        Map<String, String> headers = Map.of(FakeWebhookSignature.HEADER, FakeWebhookSignature.sign(payload, "wrong-secret"));

        assertThatThrownBy(() -> gateway.parseWebhook(payload, headers))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void webhookWithoutSignatureIsRejected() {
        assertThatThrownBy(() -> gateway.parseWebhook("{}", Map.of()))
                .isInstanceOf(InvalidWebhookException.class);
    }

    // Alterar o body depois de assinado invalida a assinatura: é isto que impede forjar um "pagamento feito"
    @Test
    void tamperedPayloadIsRejected() {
        String original = """
                {"type": "payment.failed", "paymentId": "fake_1"}
                """;
        String tampered = original.replace("payment.failed", "payment.succeeded");

        assertThatThrownBy(() -> gateway.parseWebhook(tampered, signed(original)))
                .isInstanceOf(InvalidWebhookException.class);
    }

    private static Map<String, String> signed(String payload) {
        return Map.of(FakeWebhookSignature.HEADER, FakeWebhookSignature.sign(payload, SECRET));
    }
}
