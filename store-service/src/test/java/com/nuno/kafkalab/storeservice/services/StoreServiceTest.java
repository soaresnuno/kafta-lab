package com.nuno.kafkalab.storeservice.services;

import com.nuno.kafkalab.outbox.EventPublisher;
import com.nuno.kafkalab.storeservice.config.KafkaConfig;
import com.nuno.kafkalab.storeservice.dtos.CreateStoreRequest;
import com.nuno.kafkalab.storeservice.dtos.UpdateStoreRequest;
import com.nuno.kafkalab.storeservice.entities.Store;
import com.nuno.kafkalab.storeservice.entities.StoreStatus;
import com.nuno.kafkalab.storeservice.events.StoreCreatedEvent;
import com.nuno.kafkalab.storeservice.events.StoreDeactivatedEvent;
import com.nuno.kafkalab.storeservice.exceptions.StoreAccessDeniedException;
import com.nuno.kafkalab.storeservice.exceptions.StoreInactiveException;
import com.nuno.kafkalab.storeservice.exceptions.StoreNotFoundException;
import com.nuno.kafkalab.storeservice.repositories.StoreRepository;
import com.nuno.kafkalab.storeservice.responses.StoreResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EventPublisher eventPublisher;
    @InjectMocks
    private StoreService service;

    @Test
    void createMakesTheUserTheOwnerAndPublishesStoreCreated() {
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(STORE_ID);
            return store;
        });

        StoreResponse response = service.create(new CreateStoreRequest("Keyboard Shop", "hello@shop.com"), ALICE);

        assertThat(response.ownerId()).isEqualTo(ALICE);
        assertThat(response.status()).isEqualTo(StoreStatus.ACTIVE);
        verify(eventPublisher).publish(KafkaConfig.STORE_EVENTS, STORE_ID, StoreCreatedEvent.TYPE,
                new StoreCreatedEvent(STORE_ID, ALICE, "Keyboard Shop"));
    }

    @Test
    void ownerCanUpdateTheStore() {
        givenStore(StoreStatus.ACTIVE);

        StoreResponse response = service.update(STORE_ID, updateRequest(), ALICE);

        assertThat(response.name()).isEqualTo("Keyboard Shop Lisboa");
    }

    @Test
    void anotherUserCannotUpdateTheStore() {
        Store store = givenStore(StoreStatus.ACTIVE);

        assertThatThrownBy(() -> service.update(STORE_ID, updateRequest(), BOB))
                .isInstanceOf(StoreAccessDeniedException.class);
        assertThat(store.getName()).isEqualTo("Keyboard Shop");
    }

    @Test
    void inactiveStoreCannotBeUpdated() {
        givenStore(StoreStatus.INACTIVE);

        assertThatThrownBy(() -> service.update(STORE_ID, updateRequest(), ALICE))
                .isInstanceOf(StoreInactiveException.class);
    }

    @Test
    void deactivatePublishesStoreDeactivated() {
        Store store = givenStore(StoreStatus.ACTIVE);

        service.deactivate(STORE_ID, ALICE);

        assertThat(store.getStatus()).isEqualTo(StoreStatus.INACTIVE);
        verify(eventPublisher).publish(KafkaConfig.STORE_EVENTS, STORE_ID, StoreDeactivatedEvent.TYPE,
                new StoreDeactivatedEvent(STORE_ID));
    }

    @Test
    void deactivatingAnInactiveStoreDoesNothing() {
        givenStore(StoreStatus.INACTIVE);

        service.deactivate(STORE_ID, ALICE);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void anotherUserCannotDeactivateTheStore() {
        Store store = givenStore(StoreStatus.ACTIVE);

        assertThatThrownBy(() -> service.deactivate(STORE_ID, BOB))
                .isInstanceOf(StoreAccessDeniedException.class);
        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unknownStoreIsNotFound() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(STORE_ID))
                .isInstanceOf(StoreNotFoundException.class);
    }

    private Store givenStore(StoreStatus status) {
        Store store = new Store();
        store.setId(STORE_ID);
        store.setOwnerId(ALICE);
        store.setName("Keyboard Shop");
        store.setEmail("hello@shop.com");
        store.setStatus(status);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        return store;
    }

    private static UpdateStoreRequest updateRequest() {
        return new UpdateStoreRequest("Keyboard Shop Lisboa", "lisboa@shop.com");
    }
}
