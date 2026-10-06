package com.nuno.kafkalab.paymentservice.controllers;

import com.nuno.kafkalab.paymentservice.config.SecurityConfig;
import com.nuno.kafkalab.paymentservice.exceptions.InvalidWebhookException;
import com.nuno.kafkalab.paymentservice.services.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentWebhookController.class)
@Import(SecurityConfig.class)
class PaymentWebhookControllerTest {

    private static final String PAYLOAD = "{\"type\": \"payment.succeeded\", \"paymentId\": \"fake_1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // O fornecedor não tem JWT: o endpoint é público e passa o body e os headers tal como chegaram
    @Test
    void webhookIsPublicAndPassesTheRawBodyAndHeaders() throws Exception {
        mockMvc.perform(post("/payments/webhooks/{provider}", "fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Fake-Signature", "abc123")
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(paymentService).handleWebhook(eq("fake"), eq(PAYLOAD), headers.capture());
        // Os nomes dos headers não distinguem maiúsculas, tal como em HTTP
        assertThat(headers.getValue().get("fake-signature")).isEqualTo("abc123");
    }

    @Test
    void invalidWebhookReturns400() throws Exception {
        doThrow(new InvalidWebhookException("Invalid signature"))
                .when(paymentService).handleWebhook(anyString(), anyString(), any());

        mockMvc.perform(post("/payments/webhooks/{provider}", "fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid webhook"));
    }
}
