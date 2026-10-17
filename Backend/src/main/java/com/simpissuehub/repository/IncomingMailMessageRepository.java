package com.simpissuehub.repository;

import com.simpissuehub.domain.IncomingMailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncomingMailMessageRepository extends JpaRepository<IncomingMailMessage, Long> {
    Optional<IncomingMailMessage> findByMessageId(String messageId);
    @Modifying
    @Query("delete from IncomingMailMessage message where message.receivedAt < :cutoff")
    int deleteReceivedBefore(@Param("cutoff") Instant cutoff);
}
