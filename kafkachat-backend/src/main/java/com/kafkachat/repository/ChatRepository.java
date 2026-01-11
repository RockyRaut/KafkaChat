package com.kafkachat.repository;

import com.kafkachat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("""
        SELECT c FROM Chat c
        JOIN c.participants p
        WHERE c.chatType = 'PRIVATE'
        AND p.user.id IN :userIds
        GROUP BY c.id
        HAVING COUNT(DISTINCT p.user.id) = 2
    """)
    List<Chat> findPrivateChatBetweenUsers(
            @Param("userIds") Set<Long> userIds
    );
}
