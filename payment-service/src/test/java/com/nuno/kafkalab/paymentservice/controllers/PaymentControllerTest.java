package com.nuno.kafkalab.paymentservice.controllers;

import com.nuno.kafkalab.paymentservice.config.SecurityConfig;
import com.nuno.kafkalab.paymentservice.entities.PaymentStatus;
import com.nuno.kafkalab.paymentservice.exceptions.PaymentNotFoundException;
import com.nuno.kafkalab.paymentservice.responses.PaymentResponse;
import com.nuno.kafkalab.paymentservice.services.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    // Substitui o decoder real para os testes não precisarem do Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void paymentRequiresAToken() throws Exception {
        mockMvc.perform(get("/payments/orders/{orderId}", ORDER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerSeesThePaymentAndWhereToPay() throws Exception {
        when(paymentService.getForOrder(ORDER_ID, CAROL)).thenReturn(new PaymentResponse(UUID.randomUUID(), ORDER_ID,
                new BigDecimal("200.00"), "EUR", PaymentStatus.PENDING, "http://pay.test/checkout/fake_1", null));

        mockMvc.perform(get("/payments/orders/{orderId}", ORDER_ID).with(user(CAROL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl").value("http://pay.test/checkout/fake_1"));
    }

    @Test
    void paymentOfAnotherUserReturns404() throws Exception {
        when(paymentService.getForOrder(ORDER_ID, BOB)).thenThrow(new PaymentNotFoundException(ORDER_ID));

        mockMvc.perform(get("/payments/orders/{orderId}", ORDER_ID).with(user(BOB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    // Simula um pedido com um JWT válido cujo "sub" é o utilizador dado
    private static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }
}
