package com.chatapp.backend.model.dto;

import com.chatapp.backend.model.InvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RoomInvitationDto {
    private Long id;
    private Long roomId;
    private String roomName;
    private String invitedByUsername;
    private InvitationStatus status;
    private Instant createdAt;
}