package com.nuno.kafkalab.productservice.controllers;

import com.nuno.kafkalab.productservice.dtos.CreateProductRequest;
import com.nuno.kafkalab.productservice.dtos.UpdateProductRequest;
import com.nuno.kafkalab.productservice.responses.ProductResponse;
import com.nuno.kafkalab.productservice.security.CurrentUser;
import com.nuno.kafkalab.productservice.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Gestão dos produtos de uma loja. Exige token, e o ProductService confirma que quem pede é o dono da loja.
// @AuthenticationPrincipal Jwt: o token já validado pelo Spring Security
@RestController
@RequestMapping("/stores/{storeId}/products")
@RequiredArgsConstructor
public class StoreProductController {
    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getAll(@PathVariable UUID storeId, @AuthenticationPrincipal Jwt jwt) {
        return productService.getByStore(storeId, CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@PathVariable UUID storeId, @Valid @RequestBody CreateProductRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return productService.create(storeId, request, CurrentUser.id(jwt));
    }

    @PutMapping("/{productId}")
    public ProductResponse update(@PathVariable UUID storeId, @PathVariable UUID productId,
                                  @Valid @RequestBody UpdateProductRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return productService.update(storeId, productId, request, CurrentUser.id(jwt));
    }

    // O produto não é apagado da BD: fica inativo (ver ProductService.delete)
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID storeId, @PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt) {
        productService.delete(storeId, productId, CurrentUser.id(jwt));
    }
}
