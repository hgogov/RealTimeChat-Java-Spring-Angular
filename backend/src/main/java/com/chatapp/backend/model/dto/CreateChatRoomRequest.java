package com.chatapp.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateChatRoomRequest {
    @NotBlank(message = "Room name cannot be blank")
    @Size(min = 3, max = 100, message = "Room name must be between 3 and 100 characters")
    private String name;

    private boolean isPublic = true;
}