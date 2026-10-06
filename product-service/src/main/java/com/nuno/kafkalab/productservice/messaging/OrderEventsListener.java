package com.nuno.kafkalab.productservice.messaging;

import com.nuno.kafkalab.productservice.config.KafkaConfig;
import com.nuno.kafkalab.productservice.events.OrderCancelledEvent;
import com.nuno.kafkalab.productservice.events.OrderCreatedEvent;
import com.nuno.kafkalab.productservice.services.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Um listener por tópico. O deserializer usa o header __TypeId__ para criar o record certo,
// e o Spring chama o @KafkaHandler cujo parâmetro tem esse tipo
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaConfig.ORDER_EVENTS)
public class OrderEventsListener {

    private final StockReservationService stockReservationService;

    @KafkaHandler
    public void onOrderCreated(OrderCreatedEvent event) {
        stockReservationService.reserve(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelledEvent event) {
        stockReservationService.release(event);
    }
}
