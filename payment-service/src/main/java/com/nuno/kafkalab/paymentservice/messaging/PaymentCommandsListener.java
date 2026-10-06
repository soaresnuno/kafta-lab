package com.nuno.kafkalab.paymentservice.messaging;

import com.nuno.kafkalab.paymentservice.config.KafkaConfig;
import com.nuno.kafkalab.paymentservice.events.CancelPaymentCommand;
import com.nuno.kafkalab.paymentservice.events.RequestPaymentCommand;
import com.nuno.kafkalab.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Um listener por tópico. O deserializer usa o header __TypeId__ para criar o record certo,
// e o Spring chama o @KafkaHandler cujo parâmetro tem esse tipo
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaConfig.PAYMENT_COMMANDS)
public class PaymentCommandsListener {

    private final PaymentService paymentService;

    @KafkaHandler
    public void onRequestPayment(RequestPaymentCommand command) {
        paymentService.request(command);
    }

    @KafkaHandler
    public void onCancelPayment(CancelPaymentCommand command) {
        paymentService.cancel(command);
    }
}
