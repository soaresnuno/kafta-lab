package com.nuno.kafkalab.paymentservice.services;

import com.nuno.kafkalab.outbox.EventPublisher;
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
import com.nuno.kafkalab.paymentservice.gateway.PaymentRequest;
import com.nuno.kafkalab.paymentservice.gateway.ProviderPayment;
import com.nuno.kafkalab.paymentservice.gateway.ProviderUpdate;
import com.nuno.kafkalab.paymentservice.repositories.PaymentRepository;
import com.nuno.kafkalab.paymentservice.responses.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Regras dos pagamentos. Só conhece a porta PaymentGateway, nunca um fornecedor concreto.
// Todos os métodos são idempotentes: comandos do Kafka e webhooks podem chegar repetidos
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateways gateways;
    private final EventPublisher eventPublisher;

    @Transactional
    public void request(RequestPaymentCommand command) {
        if (paymentRepository.existsByOrderId(command.orderId())) {
            log.info("Payment for order {} already requested, ignoring duplicate", command.orderId());
            return;
        }

        PaymentGateway gateway = gateways.active();
        Payment payment = new Payment();
        payment.setOrderId(command.orderId());
        payment.setUserId(command.userId());
        payment.setAmount(command.amount());
        payment.setCurrency(command.currency());
        payment.setProvider(gateway.name());
        payment.setCreatedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        try {
            ProviderPayment created = gateway.createPayment(
                    new PaymentRequest(saved.getId(), saved.getOrderId(), saved.getAmount(), saved.getCurrency()));
            saved.setProviderPaymentId(created.providerPaymentId());
            saved.setCheckoutUrl(created.checkoutUrl());
            log.info("Payment {} created for order {}, waiting for the customer to pay", saved.getId(), saved.getOrderId());
        } catch (RuntimeException e) {
            // O fornecedor está em baixo ou recusou logo o pedido: a encomenda não pode ser paga
            log.warn("Payment provider {} failed for order {}", gateway.name(), saved.getOrderId(), e);
            fail(saved, "Payment provider unavailable");
        }
    }

    // A encomenda foi cancelada: cancela o que ainda não foi pago, reembolsa o que já foi
    @Transactional
    public void cancel(CancelPaymentCommand command) {
        Optional<Payment> found = paymentRepository.findByOrderId(command.orderId());
        if (found.isEmpty()) {
            log.info("No payment for order {}, nothing to cancel", command.orderId());
            return;
        }

        Payment payment = found.get();
        switch (payment.getStatus()) {
            case PENDING -> {
                gatewayOf(payment).cancel(payment.getProviderPaymentId());
                payment.setStatus(PaymentStatus.CANCELLED);
                log.info("Payment {} cancelled", payment.getId());
            }
            case SUCCEEDED -> refund(payment);
            default -> log.info("Payment {} is {}, nothing to cancel", payment.getId(), payment.getStatus());
        }
    }

    // Chamado pelo PaymentWebhookController quando o fornecedor avisa que um pagamento mudou
    @Transactional
    public void handleWebhook(String provider, String payload, Map<String, String> headers) {
        ProviderUpdate update = gateways.byName(provider).parseWebhook(payload, headers);

        Optional<Payment> found = paymentRepository.findByProviderAndProviderPaymentId(provider, update.providerPaymentId());
        if (found.isEmpty()) {
            // Responde 200 na mesma: se respondesse erro, o fornecedor ia repetir o webhook para sempre
            log.warn("Webhook from {} for unknown payment {}, ignoring", provider, update.providerPaymentId());
            return;
        }

        Payment payment = found.get();
        switch (payment.getStatus()) {
            case PENDING -> {
                if (update.outcome() == ProviderUpdate.Outcome.SUCCEEDED) {
                    succeed(payment);
                } else {
                    fail(payment, update.failureReason() != null ? update.failureReason() : "Payment failed");
                }
            }
            // O cliente pagou depois de a encomenda ser cancelada (ex: expirou enquanto pagava): devolver o dinheiro
            case CANCELLED -> {
                if (update.outcome() == ProviderUpdate.Outcome.SUCCEEDED) {
                    refund(payment);
                }
            }
            default -> log.info("Payment {} is already {}, ignoring repeated webhook", payment.getId(), payment.getStatus());
        }
    }

    // Só quem fez a encomenda vê o pagamento; para os outros responde 404, como se não existisse
    @Transactional(readOnly = true)
    public PaymentResponse getForOrder(UUID orderId, UUID userId) {
        return paymentRepository.findByOrderIdAndUserId(orderId, userId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private void succeed(Payment payment) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        eventPublisher.publish(KafkaConfig.PAYMENT_EVENTS, payment.getOrderId(), PaymentSucceededEvent.TYPE,
                new PaymentSucceededEvent(payment.getOrderId(), payment.getId()));
        log.info("Payment {} succeeded", payment.getId());
    }

    private void fail(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        eventPublisher.publish(KafkaConfig.PAYMENT_EVENTS, payment.getOrderId(), PaymentFailedEvent.TYPE,
                new PaymentFailedEvent(payment.getOrderId(), reason));
        log.info("Payment {} failed: {}", payment.getId(), reason);
    }

    private void refund(Payment payment) {
        gatewayOf(payment).refund(payment.getProviderPaymentId(), payment.getAmount(), payment.getCurrency());
        payment.setStatus(PaymentStatus.REFUNDED);
        log.info("Payment {} refunded", payment.getId());
    }

    // Cada pagamento é tratado no fornecedor que o criou, mesmo que o fornecedor ativo tenha mudado
    private PaymentGateway gatewayOf(Payment payment) {
        return gateways.byName(payment.getProvider());
    }
}
