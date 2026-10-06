package com.nuno.kafkalab.authservice.controllers;

import com.nuno.kafkalab.authservice.dtos.LoginRequest;
import com.nuno.kafkalab.authservice.dtos.RegisterRequest;
import com.nuno.kafkalab.authservice.responses.RegisterResponse;
import com.nuno.kafkalab.authservice.responses.TokenResponse;
import com.nuno.kafkalab.authservice.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
