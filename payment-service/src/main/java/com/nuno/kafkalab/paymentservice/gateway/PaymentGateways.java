package com.nuno.kafkalab.paymentservice.gateway;

import com.nuno.kafkalab.paymentservice.config.PaymentProperties;
import com.nuno.kafkalab.paymentservice.exceptions.UnknownPaymentProviderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Registo de todos os fornecedores disponíveis (todos os beans PaymentGateway), por nome.
// Pagamentos novos vão para o fornecedor ativo; cancelar, reembolsar e webhooks vão para o fornecedor
// que criou o pagamento, mesmo que entretanto o ativo tenha mudado
@Component
public class PaymentGateways {

    private final Map<String, PaymentGateway> gateways;
    private final PaymentGateway active;

    public PaymentGateways(List<PaymentGateway> gateways, PaymentProperties properties) {
        this.gateways = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::name, Function.identity()));
        // Falha logo no arranque se payments.provider apontar para um fornecedor que não existe
        this.active = byName(properties.provider());
    }

    public PaymentGateway active() {
        return active;
    }

    public PaymentGateway byName(String name) {
        PaymentGateway gateway = gateways.get(name);
        if (gateway == null) {
            throw new UnknownPaymentProviderException(name);
        }
        return gateway;
    }
}
