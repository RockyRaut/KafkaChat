package com.kafkachat.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TypingEventDTO {
    private Long chatId;
    private Long userId;
    private String username;
    private boolean isTyping;
}

