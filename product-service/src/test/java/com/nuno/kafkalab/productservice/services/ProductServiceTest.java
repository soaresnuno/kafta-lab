package com.nuno.kafkalab.productservice.services;

import com.nuno.kafkalab.productservice.dtos.CreateProductRequest;
import com.nuno.kafkalab.productservice.dtos.UpdateProductRequest;
import com.nuno.kafkalab.productservice.entities.Product;
import com.nuno.kafkalab.productservice.entities.StoreReplica;
import com.nuno.kafkalab.productservice.exceptions.ProductNotFoundException;
import com.nuno.kafkalab.productservice.exceptions.StoreAccessDeniedException;
import com.nuno.kafkalab.productservice.exceptions.StoreInactiveException;
import com.nuno.kafkalab.productservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.productservice.repositories.ProductRepository;
import com.nuno.kafkalab.productservice.repositories.StoreReplicaRepository;
import com.nuno.kafkalab.productservice.responses.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StoreReplicaRepository storeRepository;
    @InjectMocks
    private ProductService service;

    @Test
    void ownerCreatesProductInTheirStore() {
        givenStore(true);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.create(STORE_ID, createRequest(), OWNER_ID);

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.name()).isEqualTo("Keyboard");
        assertThat(response.stock()).isEqualTo(5);
        assertThat(response.active()).isTrue();
    }

    @Test
    void createInUnknownStoreIsNotFound() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(STORE_ID, createRequest(), OWNER_ID))
                .isInstanceOf(StoreNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createInStoreOfAnotherUserIsDenied() {
        givenStore(true);

        assertThatThrownBy(() -> service.create(STORE_ID, createRequest(), OTHER_USER_ID))
                .isInstanceOf(StoreAccessDeniedException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createInInactiveStoreIsRejected() {
        givenStore(false);

        assertThatThrownBy(() -> service.create(STORE_ID, createRequest(), OWNER_ID))
                .isInstanceOf(StoreInactiveException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteDeactivatesTheProduct() {
        givenStore(true);
        Product product = product();
        when(productRepository.findByIdAndStoreIdAndActiveTrue(product.getId(), STORE_ID)).thenReturn(Optional.of(product));

        service.delete(STORE_ID, product.getId(), OWNER_ID);

        assertThat(product.isActive()).isFalse();
    }

    @Test
    void updateOfProductFromAnotherStoreIsNotFound() {
        givenStore(true);
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdAndStoreIdAndActiveTrue(productId, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(STORE_ID, productId, updateRequest(), OWNER_ID))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void storeProductsAreOnlyVisibleToTheOwner() {
        givenStore(true);

        assertThatThrownBy(() -> service.getByStore(STORE_ID, OTHER_USER_ID))
                .isInstanceOf(StoreAccessDeniedException.class);
        verifyNoInteractions(productRepository);
    }

    private void givenStore(boolean active) {
        StoreReplica store = new StoreReplica();
        store.setId(STORE_ID);
        store.setOwnerId(OWNER_ID);
        store.setActive(active);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }

    private static Product product() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setStoreId(STORE_ID);
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        return product;
    }

    private static CreateProductRequest createRequest() {
        return new CreateProductRequest("Keyboard", "75% layout", new BigDecimal("100.00"), 5);
    }

    private static UpdateProductRequest updateRequest() {
        return new UpdateProductRequest("Keyboard", "75% layout", new BigDecimal("90.00"), 3);
    }
}
