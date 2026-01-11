package com.kafkachat.service;

import com.kafkachat.dto.ChatMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaProducerService {
    private final KafkaTemplate<String, ChatMessageDTO> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, ChatMessageDTO> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendChatMessage(ChatMessageDTO message) {
        try {
            String key = "chat-" + message.getChatId();
            log.info("Publishing message to Kafka topic: {}, key: {}", "chat-messages", key);
            kafkaTemplate.send("chat-messages", key, message);
        } catch (Exception e) {
            log.error("Error sending message to Kafka", e);
        }
    }

    public void publishChatEvent(String eventType, ChatMessageDTO message) {
        try {
            log.info("Publishing event to Kafka: {}", eventType);
            kafkaTemplate.send("chat-events", message.getChatId().toString(), message);
        } catch (Exception e) {
            log.error("Error publishing event to Kafka", e);
        }
    }
}
