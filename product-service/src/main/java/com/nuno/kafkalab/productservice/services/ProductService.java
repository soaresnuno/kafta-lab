package com.nuno.kafkalab.productservice.services;

import com.nuno.kafkalab.productservice.dtos.CreateProductRequest;
import com.nuno.kafkalab.productservice.dtos.UpdateProductRequest;
import com.nuno.kafkalab.productservice.entities.Product;
import com.nuno.kafkalab.productservice.exceptions.ProductNotFoundException;
import com.nuno.kafkalab.productservice.repositories.ProductRepository;
import com.nuno.kafkalab.productservice.responses.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());

        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = findOrThrow(id);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());

        // Não é preciso chamar save(): a entidade está "managed" dentro da transação,
        // e o Hibernate faz o UPDATE sozinho no commit (dirty checking)
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
