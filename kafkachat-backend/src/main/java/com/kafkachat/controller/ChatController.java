package com.kafkachat.controller;

import com.kafkachat.dto.ChatDTO;
import com.kafkachat.dto.ChatMessageDTO;
import com.kafkachat.service.ChatService;
import com.kafkachat.service.KafkaProducerService;
import com.kafkachat.service.MessageService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final KafkaProducerService kafkaProducerService;
    private final MessageService messageService;
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatDTO> createChat(@RequestBody Map<String, Long> request) {
        try {
            Long user1Id = request.get("user1Id");
            Long user2Id = request.get("user2Id");

            if (user1Id == null || user2Id == null) {
                return ResponseEntity.badRequest().build();
            }

            ChatDTO chat = chatService.createOrGetPrivateChat(user1Id, user2Id);
            return ResponseEntity.ok(chat);

        } catch (Exception e) {
            e.printStackTrace(); // TEMP: replace with logger
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/{chatId}/messages")
    public List<ChatMessageDTO> getMessages(@PathVariable Long chatId) {
        return messageService.getMessagesByChat(chatId);
    }

    /**
     * Create or get existing private chat between two users.
     * Request body: { "user1Id": 1, "user2Id": 2 }
     * Returns: ChatDTO with id, participantIds, etc.
     */
    @PostMapping
    public ResponseEntity<ChatDTO> createChat(@RequestBody Map<String, Long> request) {
        Long user1Id = request.get("user1Id");
        Long user2Id = request.get("user2Id");
        
        if (user1Id == null || user2Id == null) {
            return ResponseEntity.badRequest().build();
        }
        
        ChatDTO chat = chatService.createOrGetPrivateChat(user1Id, user2Id);
        return ResponseEntity.ok(chat);
    }

    /**
     * Get chat by ID
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<ChatDTO> getChat(@PathVariable Long chatId) {
        try {
            ChatDTO chat = chatService.getChat(chatId);
            return ResponseEntity.ok(chat);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}