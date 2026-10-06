package com.nuno.kafkalab.orderservice.controllers;

import com.nuno.kafkalab.orderservice.config.SecurityConfig;
import com.nuno.kafkalab.orderservice.dtos.CreateOrderItemRequest;
import com.nuno.kafkalab.orderservice.dtos.CreateOrderRequest;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import com.nuno.kafkalab.orderservice.exceptions.InvalidOrderStatusException;
import com.nuno.kafkalab.orderservice.exceptions.OrderNotFoundException;
import com.nuno.kafkalab.orderservice.responses.OrderResponse;
import com.nuno.kafkalab.orderservice.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    // Substitui o decoder real para os testes não precisarem do Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void ordersRequireAToken() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void createUsesTheUserFromTheToken() throws Exception {
        when(orderService.create(any(), eq(CAROL))).thenReturn(order());

        mockMvc.perform(post("/orders").with(user(CAROL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(orderService).create(new CreateOrderRequest(List.of(new CreateOrderItemRequest(PRODUCT_ID, 2))), CAROL);
    }

    // Mesmo que o body traga um userId, quem encomenda é sempre o utilizador do token
    @Test
    void userIdInTheBodyIsIgnored() throws Exception {
        when(orderService.create(any(), eq(CAROL))).thenReturn(order());

        mockMvc.perform(post("/orders").with(user(CAROL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "%s", "items": [{"productId": "%s", "quantity": 2}]}
                                """.formatted(BOB, PRODUCT_ID)))
                .andExpect(status().isCreated());

        verify(orderService).create(any(), eq(CAROL));
    }

    @Test
    void emptyItemsReturn400() throws Exception {
        mockMvc.perform(post("/orders").with(user(CAROL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": []}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void orderOfAnotherUserReturns404() throws Exception {
        when(orderService.getById(ORDER_ID, BOB)).thenThrow(new OrderNotFoundException(ORDER_ID));

        mockMvc.perform(get("/orders/{id}", ORDER_ID).with(user(BOB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    void cancellingAFinishedOrderReturns409() throws Exception {
        when(orderService.cancel(ORDER_ID, CAROL))
                .thenThrow(new InvalidOrderStatusException(ORDER_ID, OrderStatus.CANCELLED));

        mockMvc.perform(post("/orders/{id}/cancel", ORDER_ID).with(user(CAROL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid order status"));
    }

    // Simula um pedido com um JWT válido cujo "sub" é o utilizador dado
    private static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private static String orderJson() {
        return """
                {"items": [{"productId": "%s", "quantity": 2}]}
                """.formatted(PRODUCT_ID);
    }

    private static OrderResponse order() {
        return new OrderResponse(ORDER_ID, CAROL, OrderStatus.PENDING, null, null, List.of());
    }
}
