package com.kafkachat.dto;

import lombok.*;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatDTO {
    private Long id;
    private String name;
    private String chatImage;
    private String chatType;
    private Long creatorId;
    private Set<Long> participantIds;
    private String createdAt;
}

