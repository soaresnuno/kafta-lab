package com.nuno.kafkalab.storeservice.controllers;

import com.nuno.kafkalab.storeservice.config.SecurityConfig;
import com.nuno.kafkalab.storeservice.dtos.CreateStoreRequest;
import com.nuno.kafkalab.storeservice.entities.StoreStatus;
import com.nuno.kafkalab.storeservice.exceptions.StoreAccessDeniedException;
import com.nuno.kafkalab.storeservice.exceptions.StoreInactiveException;
import com.nuno.kafkalab.storeservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.storeservice.responses.StoreResponse;
import com.nuno.kafkalab.storeservice.services.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Só a camada web: controller, SecurityConfig e GlobalExceptionHandler. O StoreService é um mock.
// O @Import é preciso porque o @WebMvcTest não carrega classes @Configuration
@WebMvcTest(StoreController.class)
@Import(SecurityConfig.class)
class StoreControllerTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final String STORE_JSON = """
            {"name": "Keyboard Shop", "email": "hello@shop.com"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreService storeService;

    // Substitui o decoder real para os testes não precisarem do Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listingStoresIsPublic() throws Exception {
        when(storeService.getAll()).thenReturn(List.of(store()));

        mockMvc.perform(get("/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Keyboard Shop"));
    }

    @Test
    void myStoresRequireAToken() throws Exception {
        mockMvc.perform(get("/stores/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAToken() throws Exception {
        mockMvc.perform(post("/stores").contentType(MediaType.APPLICATION_JSON).content(STORE_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(storeService);
    }

    @Test
    void createUsesTheUserFromTheToken() throws Exception {
        when(storeService.create(any(), eq(ALICE))).thenReturn(store());

        mockMvc.perform(post("/stores").with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(STORE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(ALICE.toString()));

        verify(storeService).create(new CreateStoreRequest("Keyboard Shop", "hello@shop.com"), ALICE);
    }

    @Test
    void invalidStoreDataReturns400() throws Exception {
        mockMvc.perform(post("/stores").with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storeService);
    }

    @Test
    void updatingTheStoreOfAnotherUserReturns403() throws Exception {
        when(storeService.update(eq(STORE_ID), any(), eq(BOB))).thenThrow(new StoreAccessDeniedException(STORE_ID));

        mockMvc.perform(put("/stores/{id}", STORE_ID).with(user(BOB))
                        .contentType(MediaType.APPLICATION_JSON).content(STORE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void updatingAnInactiveStoreReturns409() throws Exception {
        when(storeService.update(eq(STORE_ID), any(), eq(ALICE))).thenThrow(new StoreInactiveException(STORE_ID));

        mockMvc.perform(put("/stores/{id}", STORE_ID).with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(STORE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Store inactive"));
    }

    @Test
    void unknownStoreReturns404() throws Exception {
        when(storeService.getById(STORE_ID)).thenThrow(new StoreNotFoundException(STORE_ID));

        mockMvc.perform(get("/stores/{id}", STORE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Store not found"));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid signature"));

        mockMvc.perform(get("/stores/mine").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // Simula um pedido com um JWT válido cujo "sub" é o utilizador dado
    private static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private static StoreResponse store() {
        return new StoreResponse(STORE_ID, ALICE, "Keyboard Shop", "hello@shop.com", StoreStatus.ACTIVE);
    }
}
