package com.nuno.kafkalab.paymentservice.gateway;

// Resultado de um webhook, já traduzido do formato do fornecedor para um formato comum
public record ProviderUpdate(String providerPaymentId, Outcome outcome, String failureReason) {

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }
}
