package com.nuno.kafkalab.storeservice.controllers;

import com.nuno.kafkalab.storeservice.dtos.CreateStoreRequest;
import com.nuno.kafkalab.storeservice.dtos.UpdateStoreRequest;
import com.nuno.kafkalab.storeservice.responses.StoreResponse;
import com.nuno.kafkalab.storeservice.security.CurrentUser;
import com.nuno.kafkalab.storeservice.services.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// GET /stores e GET /stores/{id} são públicos; o resto exige token (ver SecurityConfig).
// @AuthenticationPrincipal Jwt: o token já validado pelo Spring Security
@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;

    @GetMapping
    public List<StoreResponse> getAll() {
        return storeService.getAll();
    }

    @GetMapping("/mine")
    public List<StoreResponse> getMine(@AuthenticationPrincipal Jwt jwt) {
        return storeService.getMine(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@Valid @RequestBody CreateStoreRequest request, @AuthenticationPrincipal Jwt jwt) {
        return storeService.create(request, CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public StoreResponse getById(@PathVariable UUID id) {
        return storeService.getById(id);
    }

    @PutMapping("/{id}")
    public StoreResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStoreRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return storeService.update(id, request, CurrentUser.id(jwt));
    }

    // A loja não é apagada da BD: fica INACTIVE (ver StoreService.deactivate)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        storeService.deactivate(id, CurrentUser.id(jwt));
    }
}
