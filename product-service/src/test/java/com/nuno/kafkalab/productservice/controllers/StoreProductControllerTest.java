package com.nuno.kafkalab.productservice.controllers;

import com.nuno.kafkalab.productservice.config.SecurityConfig;
import com.nuno.kafkalab.productservice.dtos.CreateProductRequest;
import com.nuno.kafkalab.productservice.exceptions.StoreAccessDeniedException;
import com.nuno.kafkalab.productservice.exceptions.StoreInactiveException;
import com.nuno.kafkalab.productservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.productservice.responses.ProductResponse;
import com.nuno.kafkalab.productservice.services.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreProductController.class)
@Import(SecurityConfig.class)
class StoreProductControllerTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final String PRODUCT_JSON = """
            {"name": "Keyboard", "description": "75% layout", "price": 100.00, "stock": 5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // Substitui o decoder real para os testes não precisarem do Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void storeProductsRequireAToken() throws Exception {
        mockMvc.perform(get("/stores/{storeId}/products", STORE_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService);
    }

    @Test
    void createUsesTheUserFromTheToken() throws Exception {
        when(productService.create(eq(STORE_ID), any(), eq(ALICE))).thenReturn(product());

        mockMvc.perform(post("/stores/{storeId}/products", STORE_ID).with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()));

        verify(productService).create(STORE_ID,
                new CreateProductRequest("Keyboard", "75% layout", new BigDecimal("100.00"), 5), ALICE);
    }

    @Test
    void creatingInTheStoreOfAnotherUserReturns403() throws Exception {
        when(productService.create(eq(STORE_ID), any(), eq(BOB))).thenThrow(new StoreAccessDeniedException(STORE_ID));

        mockMvc.perform(post("/stores/{storeId}/products", STORE_ID).with(user(BOB))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void creatingInAnInactiveStoreReturns409() throws Exception {
        when(productService.create(eq(STORE_ID), any(), eq(ALICE))).thenThrow(new StoreInactiveException(STORE_ID));

        mockMvc.perform(post("/stores/{storeId}/products", STORE_ID).with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingInAnUnknownStoreReturns404() throws Exception {
        when(productService.create(eq(STORE_ID), any(), eq(ALICE))).thenThrow(new StoreNotFoundException(STORE_ID));

        mockMvc.perform(post("/stores/{storeId}/products", STORE_ID).with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Store not found"));
    }

    @Test
    void invalidProductDataReturns400() throws Exception {
        mockMvc.perform(post("/stores/{storeId}/products", STORE_ID).with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": -5, "stock": 1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void deleteByTheOwnerReturns204() throws Exception {
        mockMvc.perform(delete("/stores/{storeId}/products/{productId}", STORE_ID, PRODUCT_ID).with(user(ALICE)))
                .andExpect(status().isNoContent());

        verify(productService).delete(STORE_ID, PRODUCT_ID, ALICE);
    }

    @Test
    void deleteByAnotherUserReturns403() throws Exception {
        doThrow(new StoreAccessDeniedException(STORE_ID)).when(productService).delete(STORE_ID, PRODUCT_ID, BOB);

        mockMvc.perform(delete("/stores/{storeId}/products/{productId}", STORE_ID, PRODUCT_ID).with(user(BOB)))
                .andExpect(status().isForbidden());
    }

    // Simula um pedido com um JWT válido cujo "sub" é o utilizador dado
    private static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private static ProductResponse product() {
        return new ProductResponse(PRODUCT_ID, STORE_ID, "Keyboard", "75% layout", new BigDecimal("100.00"), 5, true);
    }
}
