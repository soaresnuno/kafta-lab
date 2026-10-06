package com.nuno.kafkalab.orderservice.jobs;

import com.nuno.kafkalab.orderservice.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// Procura de minuto a minuto encomendas por pagar há mais tempo que orders.payment-timeout e cancela-as
@Component
@RequiredArgsConstructor
public class UnpaidOrdersJob {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void expireUnpaidOrders() {
        orderService.expireUnpaidOrders();
    }
}
