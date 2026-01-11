package com.kafkachat.repository;

import com.kafkachat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChat_IdOrderByCreatedAtDesc(Long chatId);
    List<Message> findBySender_IdOrderByCreatedAtDesc(Long senderId);
}
