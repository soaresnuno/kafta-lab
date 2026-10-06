package com.nuno.kafkalab.productservice.services;

import com.nuno.kafkalab.productservice.dtos.CreateProductRequest;
import com.nuno.kafkalab.productservice.dtos.UpdateProductRequest;
import com.nuno.kafkalab.productservice.entities.Product;
import com.nuno.kafkalab.productservice.entities.StoreReplica;
import com.nuno.kafkalab.productservice.exceptions.ProductNotFoundException;
import com.nuno.kafkalab.productservice.exceptions.StoreInactiveException;
import com.nuno.kafkalab.productservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.productservice.repositories.ProductRepository;
import com.nuno.kafkalab.productservice.repositories.StoreReplicaRepository;
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
    private final StoreReplicaRepository storeRepository;

    // Catálogo público: só produtos ativos
    @Transactional(readOnly = true)
    public List<ProductResponse> getCatalog() {
        return productRepository.findAllByActiveTrue().stream()
                .map(ProductResponse::from)
                .toList();
    }

    // Por id devolve também produtos inativos, para quem tem uma encomenda antiga ainda os conseguir ver
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Todos os produtos da loja, incluindo os inativos
    @Transactional(readOnly = true)
    public List<ProductResponse> getByStore(UUID storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new StoreNotFoundException(storeId);
        }
        return productRepository.findAllByStoreId(storeId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse create(UUID storeId, CreateProductRequest request) {
        requireActiveStore(storeId);

        Product product = new Product();
        product.setStoreId(storeId);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());

        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(UUID storeId, UUID productId, UpdateProductRequest request) {
        Product product = findStoreProductOrThrow(storeId, productId);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());

        // Não é preciso chamar save(): a entidade está "managed" dentro da transação,
        // e o Hibernate faz o UPDATE sozinho no commit (dirty checking)
        return ProductResponse.from(product);
    }

    // Soft delete: o produto fica inativo em vez de ser apagado
    @Transactional
    public void delete(UUID storeId, UUID productId) {
        Product product = findStoreProductOrThrow(storeId, productId);
        product.setActive(false);
    }

    // A loja vem da cópia local (StoreReplica). Logo a seguir a criar uma loja, o evento
    // pode ainda não ter chegado e a loja aparece como "not found" durante uns milissegundos
    private void requireActiveStore(UUID storeId) {
        StoreReplica store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
        if (!store.isActive()) {
            throw new StoreInactiveException(storeId);
        }
    }

    // Um produto de outra loja responde 404, tal como um que não existe
    private Product findStoreProductOrThrow(UUID storeId, UUID productId) {
        return productRepository.findByIdAndStoreIdAndActiveTrue(productId, storeId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
