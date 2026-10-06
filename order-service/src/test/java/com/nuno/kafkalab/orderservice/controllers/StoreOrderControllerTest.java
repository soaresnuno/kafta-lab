package com.nuno.kafkalab.orderservice.controllers;

import com.nuno.kafkalab.orderservice.config.SecurityConfig;
import com.nuno.kafkalab.orderservice.entities.OrderStatus;
import com.nuno.kafkalab.orderservice.exceptions.StoreAccessDeniedException;
import com.nuno.kafkalab.orderservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.orderservice.responses.StoreOrderResponse;
import com.nuno.kafkalab.orderservice.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreOrderController.class)
@Import(SecurityConfig.class)
class StoreOrderControllerTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    // Substitui o decoder real para os testes não precisarem do Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void storeOrdersRequireAToken() throws Exception {
        mockMvc.perform(get("/stores/{storeId}/orders", STORE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerSeesTheStoreOrders() throws Exception {
        when(orderService.getByStore(STORE_ID, ALICE)).thenReturn(List.of(
                new StoreOrderResponse(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.CONFIRMED,
                        new BigDecimal("200.00"), List.of())));

        mockMvc.perform(get("/stores/{storeId}/orders", STORE_ID).with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(200.00));
    }

    @Test
    void anotherUserGets403() throws Exception {
        when(orderService.getByStore(STORE_ID, BOB)).thenThrow(new StoreAccessDeniedException(STORE_ID));

        mockMvc.perform(get("/stores/{storeId}/orders", STORE_ID).with(user(BOB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void unknownStoreReturns404() throws Exception {
        when(orderService.getByStore(STORE_ID, ALICE)).thenThrow(new StoreNotFoundException(STORE_ID));

        mockMvc.perform(get("/stores/{storeId}/orders", STORE_ID).with(user(ALICE)))
                .andExpect(status().isNotFound());
    }

    // Simula um pedido com um JWT válido cujo "sub" é o utilizador dado
    private static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }
}
