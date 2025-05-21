package com.chatapp.backend.controller;

import com.chatapp.backend.model.RoomInvitation;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.RoomInvitationDto;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.RoomInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Room Invitations", description = "Manage chat room invitations")
public class RoomInvitationController {

    private static final Logger log = LoggerFactory.getLogger(RoomInvitationController.class);

    private final RoomInvitationService roomInvitationService;
    private final ChatRoomService chatRoomService;
    private final UserRepository userRepository;

    public RoomInvitationController(RoomInvitationService roomInvitationService,
                                    ChatRoomService chatRoomService,
                                    UserRepository userRepository) {
        this.roomInvitationService = roomInvitationService;
        this.chatRoomService = chatRoomService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found in database"));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get all pending invitations for the current user")
    @ApiResponse(responseCode = "200", description = "List of pending invitations")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<List<RoomInvitationDto>> getPendingInvitations() {
        User currentUser = getCurrentUser();
        log.info("User '{}' fetching pending invitations.", currentUser.getUsername());
        try {
            List<RoomInvitation> invitations = roomInvitationService.getPendingInvitationsForUser(currentUser);
            List<RoomInvitationDto> dtos = invitations.stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error fetching pending invitations for user '{}': {}", currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{invitationId}/accept")
    @Operation(summary = "Accept a pending room invitation")
    @ApiResponse(responseCode = "200", description = "Invitation accepted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid invitation state (e.g., not pending)")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "403", description = "Invitation not for this user")
    @ApiResponse(responseCode = "404", description = "Invitation not found")
    public ResponseEntity<Void> acceptInvitation(
            @Parameter(description = "ID of the invitation to accept", required = true) @PathVariable Long invitationId) {
        User currentUser = getCurrentUser();
        log.info("User '{}' attempting to accept invitation ID: {}", currentUser.getUsername(), invitationId);
        try {
            chatRoomService.acceptRoomInvitationAndAddUser(invitationId, currentUser);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            log.warn("Failed to accept invitation {}: Status={}, Reason={}", invitationId, e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Unexpected error accepting invitation {} for user '{}'", invitationId, currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{invitationId}/decline")
    @Operation(summary = "Decline a pending room invitation")
    @ApiResponse(responseCode = "200", description = "Invitation declined successfully")
    @ApiResponse(responseCode = "400", description = "Invalid invitation state (e.g., not pending)")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "403", description = "Invitation not for this user")
    @ApiResponse(responseCode = "404", description = "Invitation not found")
    public ResponseEntity<Void> declineInvitation(
            @Parameter(description = "ID of the invitation to decline", required = true) @PathVariable Long invitationId) {
        User currentUser = getCurrentUser();
        log.info("User '{}' attempting to decline invitation ID: {}", currentUser.getUsername(), invitationId);
        try {
            chatRoomService.declineRoomInvitation(invitationId, currentUser);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            log.warn("Failed to decline invitation {}: Status={}, Reason={}", invitationId, e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Unexpected error declining invitation {} for user '{}'", invitationId, currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private RoomInvitationDto mapToDto(RoomInvitation invitation) {
        return RoomInvitationDto.builder()
                .id(invitation.getId())
                .roomId(invitation.getRoom().getId())
                .roomName(invitation.getRoom().getName())
                .invitedByUsername(invitation.getInvitingUser().getUsername())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}