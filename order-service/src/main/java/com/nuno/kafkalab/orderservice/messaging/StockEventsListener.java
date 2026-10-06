package com.nuno.kafkalab.orderservice.messaging;

import com.nuno.kafkalab.orderservice.config.KafkaConfig;
import com.nuno.kafkalab.orderservice.events.StockRejectedEvent;
import com.nuno.kafkalab.orderservice.events.StockReservedEvent;
import com.nuno.kafkalab.orderservice.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Um listener por tópico. O deserializer usa o header __TypeId__ para criar o record certo,
// e o Spring chama o @KafkaHandler cujo parâmetro tem esse tipo
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaConfig.STOCK_EVENTS)
public class StockEventsListener {

    private final OrderService orderService;

    @KafkaHandler
    public void onStockReserved(StockReservedEvent event) {
        orderService.confirm(event);
    }

    @KafkaHandler
    public void onStockRejected(StockRejectedEvent event) {
        orderService.reject(event);
    }
}
