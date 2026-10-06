package com.nuno.kafkalab.paymentservice.repositories;

import com.nuno.kafkalab.paymentservice.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Os mais antigos primeiro, para os eventos saírem pela ordem em que foram gravados.
    // Assume uma só instância do serviço: com várias, era preciso bloquear as linhas
    // (SELECT ... FOR UPDATE) para duas instâncias não enviarem o mesmo evento
    List<OutboxEvent> findTop100BySentAtIsNullOrderByIdAsc();

    @Modifying
    @Query("delete from OutboxEvent e where e.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
