package com.kafkachat.service;

import com.kafkachat.config.RawWebSocketConfig;
import com.kafkachat.dto.ChatMessageDTO;
import com.kafkachat.entity.Message;
import com.kafkachat.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaConsumerService {
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final RawWebSocketConfig rawWebSocketHandler;

    public KafkaConsumerService(SimpMessagingTemplate messagingTemplate,
                              MessageService messageService,
                              RawWebSocketConfig rawWebSocketHandler) {
        this.messagingTemplate = messagingTemplate;
        this.messageService = messageService;
        this.rawWebSocketHandler = rawWebSocketHandler;
    }

    @KafkaListener(topics = "chat-messages", groupId = "kafkachat-group")
    public void consumeChatMessage(ChatMessageDTO message) {
        try {
            log.info("=== Kafka Consumer: Received message from Kafka ===");
            log.info("Message ID: {}, Chat ID: {}, Sender ID: {}, Content: {}", 
                     message.getId(), message.getChatId(), message.getSenderId(), message.getContent());
            
            // Update message status to DELIVERED
            messageService.updateMessageStatus(message.getId(), Message.MessageStatus.DELIVERED);
            log.info("Message status updated to DELIVERED");
            
            // Send to STOMP subscribers (web clients)
            messagingTemplate.convertAndSend(
                "/topic/chat/" + message.getChatId(),
                message
            );
            log.info("Message sent to STOMP subscribers on topic: /topic/chat/{}", message.getChatId());
            
            // Send to ALL raw WebSocket clients (Android) - THIS IS THE KEY BROADCAST
            log.info("Broadcasting message to ALL raw WebSocket clients (Android)...");
            rawWebSocketHandler.sendToAll(message);
            
            log.info("=== Message delivery complete. Message ID: {} ===", message.getId());
        } catch (Exception e) {
            log.error("ERROR in Kafka consumer when processing message", e);
        }
    }

    @KafkaListener(topics = "chat-events", groupId = "kafkachat-group")
    public void consumeChatEvent(ChatMessageDTO event) {
        try {
            log.info("Received event from Kafka: {}", event);
            messagingTemplate.convertAndSend(
                "/topic/events/" + event.getChatId(),
                event
            );
        } catch (Exception e) {
            log.error("Error consuming chat event", e);
        }
    }
}
