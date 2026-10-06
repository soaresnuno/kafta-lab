package com.nuno.kafkalab.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Os próximos eventos por enviar, os mais antigos primeiro.
    // FOR UPDATE: bloqueia as linhas devolvidas até ao fim da transação (o relay envia e marca sent_at nela).
    // SKIP LOCKED: salta as linhas que outra transação já bloqueou, em vez de ficar à espera.
    // Com várias instâncias do mesmo serviço, cada linha é apanhada por uma só instância de cada vez.
    // Se a instância morrer a meio, a transação é desfeita, os bloqueios saem e outra instância apanha as linhas
    @Query(value = """
            select * from outbox_events
            where sent_at is null
            order by id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextPending(@Param("batchSize") int batchSize);

    // Há um evento anterior com a mesma key ainda por enviar? (ex: bloqueado por outra instância)
    boolean existsByMessageKeyAndSentAtIsNullAndIdLessThan(String messageKey, Long id);

    @Modifying
    @Query("delete from OutboxEvent e where e.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
