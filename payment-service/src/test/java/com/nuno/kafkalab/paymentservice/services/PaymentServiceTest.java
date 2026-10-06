package com.nuno.kafkalab.paymentservice.services;

import com.nuno.kafkalab.paymentservice.config.KafkaConfig;
import com.nuno.kafkalab.paymentservice.entities.Payment;
import com.nuno.kafkalab.paymentservice.entities.PaymentStatus;
import com.nuno.kafkalab.paymentservice.events.CancelPaymentCommand;
import com.nuno.kafkalab.paymentservice.events.PaymentFailedEvent;
import com.nuno.kafkalab.paymentservice.events.PaymentSucceededEvent;
import com.nuno.kafkalab.paymentservice.events.RequestPaymentCommand;
import com.nuno.kafkalab.paymentservice.exceptions.PaymentNotFoundException;
import com.nuno.kafkalab.paymentservice.gateway.PaymentGateway;
import com.nuno.kafkalab.paymentservice.gateway.PaymentGateways;
import com.nuno.kafkalab.paymentservice.gateway.ProviderPayment;
import com.nuno.kafkalab.paymentservice.gateway.ProviderUpdate;
import com.nuno.kafkalab.paymentservice.messaging.EventPublisher;
import com.nuno.kafkalab.paymentservice.repositories.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// O fornecedor é um mock da porta PaymentGateway: estes testes não dependem de nenhum fornecedor concreto
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final String PROVIDER = "test-provider";
    private static final String PROVIDER_PAYMENT_ID = "pp_123";
    private static final Map<String, String> HEADERS = Map.of("Signature", "ok");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateways gateways;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private EventPublisher eventPublisher;
    @InjectMocks
    private PaymentService service;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Test
    void requestCreatesAPendingPaymentWithTheActiveProvider() {
        when(gateways.active()).thenReturn(gateway);
        when(gateway.name()).thenReturn(PROVIDER);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(gateway.createPayment(any())).thenReturn(new ProviderPayment(PROVIDER_PAYMENT_ID, "https://pay.example/pp_123"));

        service.request(requestPayment());

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProvider()).isEqualTo(PROVIDER);
        assertThat(payment.getProviderPaymentId()).isEqualTo(PROVIDER_PAYMENT_ID);
        assertThat(payment.getCheckoutUrl()).isEqualTo("https://pay.example/pp_123");
        // Ainda ninguém pagou: não há eventos
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void duplicateRequestIsIgnored() {
        when(paymentRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

        service.request(requestPayment());

        verifyNoInteractions(gateways, eventPublisher);
    }

    @Test
    void providerErrorFailsThePayment() {
        when(gateways.active()).thenReturn(gateway);
        when(gateway.name()).thenReturn(PROVIDER);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(gateway.createPayment(any())).thenThrow(new IllegalStateException("provider down"));

        service.request(requestPayment());

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publish(KafkaConfig.PAYMENT_EVENTS, ORDER_ID, PaymentFailedEvent.TYPE,
                new PaymentFailedEvent(ORDER_ID, "Payment provider unavailable"));
    }

    @Test
    void successfulWebhookCompletesThePayment() {
        Payment payment = givenPaymentFromWebhook(PaymentStatus.PENDING, ProviderUpdate.Outcome.SUCCEEDED);

        service.handleWebhook(PROVIDER, "{}", HEADERS);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(eventPublisher).publish(KafkaConfig.PAYMENT_EVENTS, ORDER_ID, PaymentSucceededEvent.TYPE,
                new PaymentSucceededEvent(ORDER_ID, PAYMENT_ID));
    }

    @Test
    void failedWebhookFailsThePayment() {
        Payment payment = givenPaymentFromWebhook(PaymentStatus.PENDING, ProviderUpdate.Outcome.FAILED);

        service.handleWebhook(PROVIDER, "{}", HEADERS);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        verify(eventPublisher).publish(KafkaConfig.PAYMENT_EVENTS, ORDER_ID, PaymentFailedEvent.TYPE,
                new PaymentFailedEvent(ORDER_ID, "Card declined"));
    }

    @Test
    void repeatedWebhookIsIgnored() {
        Payment payment = givenPaymentFromWebhook(PaymentStatus.SUCCEEDED, ProviderUpdate.Outcome.SUCCEEDED);

        service.handleWebhook(PROVIDER, "{}", HEADERS);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void paymentCompletedAfterCancellationIsRefunded() {
        Payment payment = givenPaymentFromWebhook(PaymentStatus.CANCELLED, ProviderUpdate.Outcome.SUCCEEDED);

        service.handleWebhook(PROVIDER, "{}", HEADERS);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(gateway).refund(PROVIDER_PAYMENT_ID, payment.getAmount(), "EUR");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void webhookForUnknownPaymentIsIgnored() {
        when(gateways.byName(PROVIDER)).thenReturn(gateway);
        when(gateway.parseWebhook("{}", HEADERS))
                .thenReturn(new ProviderUpdate("pp_unknown", ProviderUpdate.Outcome.SUCCEEDED, null));
        when(paymentRepository.findByProviderAndProviderPaymentId(PROVIDER, "pp_unknown")).thenReturn(Optional.empty());

        service.handleWebhook(PROVIDER, "{}", HEADERS);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancellingAPendingPaymentCancelsItAtTheProvider() {
        Payment payment = givenPayment(PaymentStatus.PENDING);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(gateways.byName(PROVIDER)).thenReturn(gateway);

        service.cancel(new CancelPaymentCommand(ORDER_ID));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(gateway).cancel(PROVIDER_PAYMENT_ID);
    }

    @Test
    void cancellingAPaidPaymentRefundsIt() {
        Payment payment = givenPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(gateways.byName(PROVIDER)).thenReturn(gateway);

        service.cancel(new CancelPaymentCommand(ORDER_ID));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(gateway).refund(PROVIDER_PAYMENT_ID, payment.getAmount(), "EUR");
    }

    @Test
    void paymentOfAnotherUserIsNotFound() {
        UUID bob = UUID.randomUUID();
        when(paymentRepository.findByOrderIdAndUserId(ORDER_ID, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForOrder(ORDER_ID, bob))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    private Payment givenPaymentFromWebhook(PaymentStatus status, ProviderUpdate.Outcome outcome) {
        Payment payment = givenPayment(status);
        when(gateways.byName(PROVIDER)).thenReturn(gateway);
        when(gateway.parseWebhook("{}", HEADERS)).thenReturn(new ProviderUpdate(PROVIDER_PAYMENT_ID, outcome,
                outcome == ProviderUpdate.Outcome.FAILED ? "Card declined" : null));
        when(paymentRepository.findByProviderAndProviderPaymentId(PROVIDER, PROVIDER_PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        return payment;
    }

    private static Payment givenPayment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrderId(ORDER_ID);
        payment.setUserId(CAROL);
        payment.setAmount(new BigDecimal("200.00"));
        payment.setCurrency("EUR");
        payment.setStatus(status);
        payment.setProvider(PROVIDER);
        payment.setProviderPaymentId(PROVIDER_PAYMENT_ID);
        payment.setCreatedAt(Instant.now());
        return payment;
    }

    private static Payment withId(Payment payment) {
        payment.setId(PAYMENT_ID);
        return payment;
    }

    private static RequestPaymentCommand requestPayment() {
        return new RequestPaymentCommand(ORDER_ID, CAROL, new BigDecimal("200.00"), "EUR");
    }
}
