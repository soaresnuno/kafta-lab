package com.nuno.kafkalab.paymentservice.repositories;

import com.nuno.kafkalab.paymentservice.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByOrderIdAndUserId(UUID orderId, UUID userId);

    Optional<Payment> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);
}
