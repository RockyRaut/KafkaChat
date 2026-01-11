package com.kafkachat.config;

import com.kafkachat.dto.ChatMessageDTO;
import com.kafkachat.dto.TypingEventDTO;
import com.kafkachat.service.KafkaProducerService;
import com.kafkachat.service.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RawWebSocketConfig extends TextWebSocketHandler {
    
    private final KafkaProducerService kafkaProducerService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    public RawWebSocketConfig(KafkaProducerService kafkaProducerService, 
                             MessageService messageService,
                             ObjectMapper objectMapper) {
        this.kafkaProducerService = kafkaProducerService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {} (Total sessions: {})", session.getId(), sessions.size() + 1);
        sessions.put(session.getId(), session);
        log.info("Session added. Total active sessions: {}", sessions.size());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            log.info("Received message from client session: {}", session.getId());
            String payload = message.getPayload();
            
            // Parse as JSON to determine message type
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            // Check if it's a typing event
            if (jsonNode.has("isTyping")) {
                TypingEventDTO typingEvent = objectMapper.readValue(payload, TypingEventDTO.class);
                log.info("Processing typing event: chatId={}, userId={}, isTyping={}", 
                        typingEvent.getChatId(), typingEvent.getUserId(), typingEvent.isTyping());
                
                // Broadcast typing event to all other clients
                broadcastTypingEvent(typingEvent);
                return;
            }
            
            // Otherwise, it's a chat message
            ChatMessageDTO chatMessage = objectMapper.readValue(payload, ChatMessageDTO.class);
            
            log.info("Processing message: chatId={}, senderId={}, content={}", 
                    chatMessage.getChatId(), chatMessage.getSenderId(), chatMessage.getContent());
            
            // Save message to database first (will throw if chat doesn't exist)
            ChatMessageDTO saved = messageService.saveMessage(chatMessage);
            log.info("Message saved to database with ID: {}", saved.getId());
            
            // Send to Kafka for broadcasting to ALL clients
            log.info("Sending message to Kafka for broadcasting. Active sessions: {}", sessions.size());
            kafkaProducerService.sendChatMessage(saved);
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            try {
                session.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage() + "\"}"));
            } catch (IOException ioException) {
                log.error("Error sending error message", ioException);
            }
        }
    }
    
    /**
     * Broadcast typing event to all connected clients
     */
    private void broadcastTypingEvent(TypingEventDTO typingEvent) {
        try {
            String json = objectMapper.writeValueAsString(typingEvent);
            TextMessage textMessage = new TextMessage(json);
            
            int successCount = 0;
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                        successCount++;
                    } catch (IOException e) {
                        log.error("Error sending typing event to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
            log.debug("Typing event broadcast to {} sessions", successCount);
        } catch (Exception e) {
            log.error("Error broadcasting typing event", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {} (Status: {})", session.getId(), status);
        sessions.remove(session.getId());
        log.info("Session removed. Remaining active sessions: {}", sessions.size());
    }
    
    public void sendToAll(ChatMessageDTO message) {
        try {
            log.info("Broadcasting message to all clients. Active sessions: {}, Message ID: {}, Chat ID: {}", 
                    sessions.size(), message.getId(), message.getChatId());
            
            if (sessions.isEmpty()) {
                log.warn("No active WebSocket sessions to broadcast to!");
                return;
            }
            
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);
            
            // Create a copy of session IDs to avoid ConcurrentModificationException
            var sessionIds = new java.util.ArrayList<>(sessions.keySet());
            int successCount = 0;
            int failedCount = 0;
            
            for (String sessionId : sessionIds) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                        successCount++;
                        log.debug("Message sent successfully to session: {}", sessionId);
                    } catch (IOException e) {
                        log.error("Error sending message to session {}: {}", sessionId, e.getMessage());
                        failedCount++;
                        // Remove closed/broken session
                        sessions.remove(sessionId);
                    }
                } else {
                    log.debug("Session {} is closed or null, removing from active sessions", sessionId);
                    sessions.remove(sessionId);
                    failedCount++;
                }
            }
            
            log.info("Broadcast complete. Success: {}, Failed: {}, Total attempted: {}", 
                    successCount, failedCount, sessionIds.size());
        } catch (Exception e) {
            log.error("Error broadcasting message to all clients", e);
        }
    }
    
    /**
     * Get the number of active WebSocket sessions (for debugging/monitoring)
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * Get active session IDs (for debugging)
     */
    public java.util.Set<String> getActiveSessionIds() {
        return new java.util.HashSet<>(sessions.keySet());
    }
}

