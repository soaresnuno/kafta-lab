package com.nuno.kafkalab.authservice.controllers;

import com.nuno.kafkalab.authservice.dtos.LoginRequest;
import com.nuno.kafkalab.authservice.exceptions.EmailAlreadyRegisteredException;
import com.nuno.kafkalab.authservice.exceptions.InvalidCredentialsException;
import com.nuno.kafkalab.authservice.responses.RegisterResponse;
import com.nuno.kafkalab.authservice.responses.TokenResponse;
import com.nuno.kafkalab.authservice.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final String REGISTER_JSON = """
            {"email": "alice@kafkalab.dev", "password": "alice-password", "firstName": "Alice", "lastName": "Owner"}
            """;
    private static final String LOGIN_JSON = """
            {"email": "alice@kafkalab.dev", "password": "alice-password"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerReturns201WithTheUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.register(any())).thenReturn(new RegisterResponse(userId, "alice@kafkalab.dev"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void invalidRegistrationReturns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "firstName": "", "lastName": "Owner"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void registeringAnExistingEmailReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyRegisteredException("alice@kafkalab.dev"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void loginReturnsTheAccessToken() throws Exception {
        when(authService.login(new LoginRequest("alice@kafkalab.dev", "alice-password")))
                .thenReturn(new TokenResponse("jwt", "Bearer", 900));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void wrongCredentialsReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    void keycloakDownReturns503() throws Exception {
        when(authService.login(any())).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Identity provider unavailable"));
    }
}
