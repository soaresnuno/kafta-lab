package com.nuno.kafkalab.orderservice.services;

import com.nuno.kafkalab.orderservice.entities.StoreReplica;
import com.nuno.kafkalab.orderservice.events.StoreCreatedEvent;
import com.nuno.kafkalab.orderservice.events.StoreDeactivatedEvent;
import com.nuno.kafkalab.orderservice.repositories.StoreReplicaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreReplicaServiceTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Mock
    private StoreReplicaRepository storeRepository;
    @InjectMocks
    private StoreReplicaService service;

    @Captor
    private ArgumentCaptor<StoreReplica> storeCaptor;

    @Test
    void registersNewStoreWithItsOwner() {
        service.register(new StoreCreatedEvent(STORE_ID, OWNER_ID, "Keyboard Shop"));

        verify(storeRepository).save(storeCaptor.capture());
        assertThat(storeCaptor.getValue().getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(storeCaptor.getValue().isActive()).isTrue();
    }

    @Test
    void ignoresDuplicateStoreCreated() {
        when(storeRepository.existsById(STORE_ID)).thenReturn(true);

        service.register(new StoreCreatedEvent(STORE_ID, OWNER_ID, "Keyboard Shop"));

        verify(storeRepository, never()).save(any());
    }

    @Test
    void deactivationKeepsTheOwnerSoTheHistoryStaysVisible() {
        StoreReplica store = new StoreReplica();
        store.setId(STORE_ID);
        store.setOwnerId(OWNER_ID);
        store.setActive(true);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        service.deactivate(new StoreDeactivatedEvent(STORE_ID));

        assertThat(store.isActive()).isFalse();
        assertThat(store.getOwnerId()).isEqualTo(OWNER_ID);
    }
}
