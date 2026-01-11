package com.kafkachat.service;

import com.kafkachat.dto.ChatMessageDTO;
import com.kafkachat.entity.Chat;
import com.kafkachat.entity.Message;
import com.kafkachat.entity.User;
import com.kafkachat.repository.ChatRepository;
import com.kafkachat.repository.MessageRepository;
import com.kafkachat.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    public ChatMessageDTO saveMessage(ChatMessageDTO dto) {
        Chat chat = chatRepository.findById(dto.getChatId())
                .orElseThrow(() -> new NoSuchElementException("Chat not found"));
        User sender = userRepository.findById(dto.getSenderId())
                .orElseThrow(() -> new NoSuchElementException("Sender not found"));

        Message message = Message.builder()
                .chat(chat)
                .sender(sender)
                .content(dto.getContent())
                .mediaUrl(dto.getMediaUrl())
                .status(dto.getStatus() != null ? dto.getStatus() : Message.MessageStatus.SENT)
                .createdAt(LocalDateTime.now())
                .build();

        Message saved = messageRepository.save(message);
        log.info("Message {} saved for chat {}", saved.getId(), chat.getId());
        return toDto(saved);
    }

    public void updateMessageStatus(Long messageId, Message.MessageStatus status) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setStatus(status);
            if (status == Message.MessageStatus.DELIVERED) {
                message.setDeliveredAt(LocalDateTime.now());
            } else if (status == Message.MessageStatus.READ) {
                message.setReadAt(LocalDateTime.now());
            }
            messageRepository.save(message);
        });
    }

    public List<ChatMessageDTO> getMessagesByChat(Long chatId) {
        return messageRepository.findByChat_IdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ChatMessageDTO toDto(Message message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .senderImage(message.getSender().getProfileImage())
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .deliveredAt(message.getDeliveredAt())
                .readAt(message.getReadAt())
                .build();
    }
}

