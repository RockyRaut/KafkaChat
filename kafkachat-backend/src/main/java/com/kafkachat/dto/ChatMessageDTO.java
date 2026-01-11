package com.kafkachat.dto;

import com.kafkachat.entity.Message.MessageStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
    private Long id;
    private Long chatId;
    private Long senderId;
    private String senderUsername;
    private String senderImage;
    private String content;
    private String mediaUrl;
    private MessageStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
}
