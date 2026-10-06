package com.nuno.kafkalab.storeservice.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email
        ) {
}
