package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.dtos.CreateOrderRequest;
import com.nuno.kafkalab.orderservice.entities.Order;
import com.nuno.kafkalab.orderservice.entities.OrderItem;
import com.nuno.kafkalab.orderservice.exceptions.OrderNotFoundException;
import com.nuno.kafkalab.orderservice.repositories.OrderRepository;
import com.nuno.kafkalab.orderservice.responses.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.userId());

        for (var itemRequest : request.items()) {
            OrderItem item = new OrderItem();
            item.setProductId(itemRequest.productId());
            item.setQuantity(itemRequest.quantity());
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return OrderResponse.from(findOrThrow(id));
    }

    @Transactional
    public void delete(UUID id) {
        Order order = findOrThrow(id);
        orderRepository.delete(order);
    }

    private Order findOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
