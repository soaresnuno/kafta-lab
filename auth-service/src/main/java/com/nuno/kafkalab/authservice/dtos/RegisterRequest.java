package com.nuno.kafkalab.authservice.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// firstName e lastName são obrigatórios porque o Keycloak (24+) os exige no perfil:
// sem eles a conta fica "incompleta" e o login falha
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName
        ) {
}
