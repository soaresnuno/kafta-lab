package com.nuno.kafkalab.storeservice.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT substitui os dados da loja, por isso todos os campos obrigatórios continuam obrigatórios
public record UpdateStoreRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email
        ) {
}
