package com.nuno.kafkalab.storeservice.controllers;

import com.nuno.kafkalab.storeservice.dtos.CreateStoreRequest;
import com.nuno.kafkalab.storeservice.dtos.UpdateStoreRequest;
import com.nuno.kafkalab.storeservice.responses.StoreResponse;
import com.nuno.kafkalab.storeservice.services.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;

    @GetMapping
    public List<StoreResponse> getAll() {
        return storeService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@Valid @RequestBody CreateStoreRequest request) {
        return storeService.create(request);
    }

    @GetMapping("/{id}")
    public StoreResponse getById(@PathVariable UUID id) {
        return storeService.getById(id);
    }

    @PutMapping("/{id}")
    public StoreResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStoreRequest request) {
        return storeService.update(id, request);
    }

    // A loja não é apagada da BD: fica INACTIVE (ver StoreService.deactivate)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        storeService.deactivate(id);
    }
}
