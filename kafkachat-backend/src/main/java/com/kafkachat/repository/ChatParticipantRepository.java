package com.kafkachat.repository;

import com.kafkachat.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    List<ChatParticipant> findByChat_Id(Long chatId);
    List<ChatParticipant> findByUser_Id(Long userId);
}
