package com.nuno.kafkalab.productservice.controllers;

import com.nuno.kafkalab.productservice.dtos.CreateProductRequest;
import com.nuno.kafkalab.productservice.dtos.UpdateProductRequest;
import com.nuno.kafkalab.productservice.responses.ProductResponse;
import com.nuno.kafkalab.productservice.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Gestão dos produtos de uma loja. Por agora qualquer pessoa pode chamar estes endpoints;
// garantir que é mesmo o dono da loja fica para o passo do Keycloak
@RestController
@RequestMapping("/stores/{storeId}/products")
@RequiredArgsConstructor
public class StoreProductController {
    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getAll(@PathVariable UUID storeId) {
        return productService.getByStore(storeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@PathVariable UUID storeId, @Valid @RequestBody CreateProductRequest request) {
        return productService.create(storeId, request);
    }

    @PutMapping("/{productId}")
    public ProductResponse update(@PathVariable UUID storeId, @PathVariable UUID productId,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(storeId, productId, request);
    }

    // O produto não é apagado da BD: fica inativo (ver ProductService.delete)
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID storeId, @PathVariable UUID productId) {
        productService.delete(storeId, productId);
    }
}
