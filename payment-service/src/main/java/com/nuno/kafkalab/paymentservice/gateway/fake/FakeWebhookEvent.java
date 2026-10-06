package com.nuno.kafkalab.paymentservice.gateway.fake;

// Formato dos webhooks do fornecedor falso. É de propósito diferente do ProviderUpdate:
// cada fornecedor tem o seu formato, e traduzi-lo é trabalho do adapter
record FakeWebhookEvent(String type, String paymentId, String failureMessage) {

    static final String SUCCEEDED = "payment.succeeded";
    static final String FAILED = "payment.failed";
}
