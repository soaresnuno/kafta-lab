package com.nuno.kafkalab.productservice.controllers;

import com.nuno.kafkalab.productservice.responses.ProductResponse;
import com.nuno.kafkalab.productservice.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Catálogo público, só leitura. Criar, editar e apagar é feito pela loja (StoreProductController)
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getCatalog() {
        return productService.getCatalog();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        return productService.getById(id);
    }
}
