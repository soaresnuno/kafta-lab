package com.nuno.kafkalab.paymentservice.gateway;

import java.math.BigDecimal;
import java.util.Map;

// Porta (ports & adapters): tudo o que o payment-service precisa de um fornecedor de pagamentos.
// O resto do serviço só conhece esta interface; cada fornecedor (fake, Stripe, ...) é uma implementação,
// e o código de negócio nunca importa classes de um fornecedor concreto.
//
// Pagamentos reais são assíncronos: createPayment devolve um pagamento por concluir (o cliente ainda
// tem de pagar na página do fornecedor) e o resultado chega mais tarde por webhook (parseWebhook)
public interface PaymentGateway {

    // Nome guardado em cada pagamento, para cancelar e reembolsar sempre no fornecedor que cobrou
    String name();

    // Cria o pagamento no fornecedor. Devolve o id dele e o URL onde o cliente paga
    ProviderPayment createPayment(PaymentRequest request);

    // Cancela um pagamento que ainda não foi pago
    void cancel(String providerPaymentId);

    // Devolve o dinheiro de um pagamento já pago
    void refund(String providerPaymentId, BigDecimal amount, String currency);

    // Valida a assinatura do webhook e traduz o formato do fornecedor para um formato comum.
    // Os nomes dos headers não distinguem maiúsculas. Webhook inválido -> InvalidWebhookException
    ProviderUpdate parseWebhook(String payload, Map<String, String> headers);
}
