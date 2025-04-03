package com.chatapp.backend.model.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatRoomDto {
    private Long id;
    private String name;
    private String createdByUsername;
    private Instant createdAt;
    // Potentially add member count or list of member usernames
}