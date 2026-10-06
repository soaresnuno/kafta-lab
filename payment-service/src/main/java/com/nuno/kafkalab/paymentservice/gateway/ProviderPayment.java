package com.nuno.kafkalab.paymentservice.gateway;

// Pagamento criado no fornecedor: o id dele e o URL onde o cliente conclui o pagamento
public record ProviderPayment(String providerPaymentId, String checkoutUrl) {}
