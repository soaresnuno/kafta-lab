package com.nuno.kafkalab.productservice.repositories;

import com.nuno.kafkalab.productservice.PostgresContainerConfiguration;
import com.nuno.kafkalab.productservice.entities.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Só a camada JPA, contra um Postgres real num contentor: o FOR UPDATE e o UPDATE em massa
// são SQL específico que só se testa a sério na base de dados verdadeira.
// Cada teste corre numa transação que é desfeita no fim, por isso os testes não se afetam
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfiguration.class)
class ProductRepositoryTest {

    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void catalogReturnsOnlyActiveProducts() {
        Product active = save(product(STORE_A, true));
        save(product(STORE_A, false));

        assertThat(productRepository.findAllByActiveTrue())
                .extracting(Product::getId)
                .containsExactly(active.getId());
    }

    @Test
    void storeProductLookupIgnoresOtherStoresAndInactiveProducts() {
        Product keyboard = save(product(STORE_A, true));
        Product inactive = save(product(STORE_A, false));

        assertThat(productRepository.findByIdAndStoreIdAndActiveTrue(keyboard.getId(), STORE_A)).isPresent();
        assertThat(productRepository.findByIdAndStoreIdAndActiveTrue(keyboard.getId(), STORE_B)).isEmpty();
        assertThat(productRepository.findByIdAndStoreIdAndActiveTrue(inactive.getId(), STORE_A)).isEmpty();
    }

    @Test
    void lockingQueryReturnsOnlyTheRequestedProducts() {
        Product keyboard = save(product(STORE_A, true));
        Product mouse = save(product(STORE_A, true));
        save(product(STORE_B, true));

        List<Product> locked = productRepository.findAllByIdForUpdate(List.of(keyboard.getId(), mouse.getId()));

        assertThat(locked)
                .extracting(Product::getId)
                .containsExactlyInAnyOrder(keyboard.getId(), mouse.getId());
    }

    @Test
    void deactivatingAStoreDeactivatesOnlyItsProducts() {
        Product keyboard = save(product(STORE_A, true));
        Product mouse = save(product(STORE_A, true));
        Product otherStore = save(product(STORE_B, true));

        int deactivated = productRepository.deactivateAllByStoreId(STORE_A);
        // O UPDATE em massa vai direto à BD sem passar pelas entidades em memória:
        // limpar a cache do EntityManager para os findById seguintes lerem da BD
        entityManager.clear();

        assertThat(deactivated).isEqualTo(2);
        assertThat(productRepository.findById(keyboard.getId()).orElseThrow().isActive()).isFalse();
        assertThat(productRepository.findById(mouse.getId()).orElseThrow().isActive()).isFalse();
        assertThat(productRepository.findById(otherStore.getId()).orElseThrow().isActive()).isTrue();
    }

    private Product save(Product product) {
        return productRepository.saveAndFlush(product);
    }

    private static Product product(UUID storeId, boolean active) {
        Product product = new Product();
        product.setStoreId(storeId);
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setActive(active);
        return product;
    }
}
