package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.ChatRoomDto;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.model.dto.InviteUserRequest;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/rooms")
@Tag(name = "Chat Rooms", description = "Manage Chat Rooms")
public class ChatRoomController {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomController.class);

    private final ChatRoomService chatRoomService;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomController(ChatRoomService chatRoomService, UserRepository userRepository, ChatRoomRepository chatRoomRepository) {
        this.chatRoomService = chatRoomService;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Authenticated user '{}' not found in database!", username);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found in database");
                });
    }

    @PostMapping
    @Operation(summary = "Create a new chat room", description = "Creates a public or private chat room. Creator automatically joins.")
    @ApiResponse(responseCode = "201", description = "Chat room created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatRoomDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request data or room name already exists")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<ChatRoomDto> createChatRoom(@Valid @RequestBody CreateChatRoomRequest request) {
        log.info("Received request to create room: Name='{}', IsPublic='{}'", request.getName(), request.getIsPublic());
        User currentUser = getCurrentUser();
        try {
            ChatRoom savedRoom = chatRoomService.createRoom(request, currentUser);
            return new ResponseEntity<>(mapToDto(savedRoom), HttpStatus.CREATED);
        } catch (ResponseStatusException e) {
            log.warn("Failed to create room '{}': {}", request.getName(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(null);
        } catch (Exception e) {
            log.error("Unexpected error creating room '{}'", request.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping
    @Operation(summary = "Get chat rooms the current user is a member of")
    @ApiResponse(responseCode = "200", description = "List of user's chat rooms", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatRoomDto.class)))
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<List<ChatRoomDto>> getUserChatRooms() {
        log.info("Received request to get user rooms");
        User currentUser = getCurrentUser();
        try {
            List<ChatRoom> rooms = chatRoomService.findRoomsForUser(currentUser);
            List<ChatRoomDto> roomDtos = rooms.stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(roomDtos);
        } catch (Exception e) {
            log.error("Unexpected error fetching rooms for user '{}'", currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    private ChatRoomDto mapToDto(ChatRoom room) {
        return ChatRoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .createdByUsername(room.getCreatedBy() != null ? room.getCreatedBy().getUsername() : null)
                .createdAt(room.getCreatedAt())
                .isPublic(room.isPublic())
                .build();
    }

    @PostMapping("/{roomId}/join")
    @Operation(summary = "Join an existing chat room")
    @ApiResponse(responseCode = "200", description = "Successfully joined room")
    @ApiResponse(responseCode = "400", description = "User already in room")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "404", description = "Room or User not found")
    public ResponseEntity<Void> joinChatRoom(
            @Parameter(description = "ID of the room to join") @PathVariable Long roomId) {
        User currentUser = getCurrentUser();
        log.info("Received request for user '{}' to join room ID: {}", currentUser.getUsername(), roomId);
        try {
            chatRoomService.joinRoom(roomId, currentUser);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            log.warn("Failed to join room {}: Status={}, Reason={}", roomId, e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Unexpected error joining room {} for user '{}'", roomId, currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{roomId}/leave")
    @Operation(summary = "Leave a chat room")
    @ApiResponse(responseCode = "200", description = "Successfully left room")
    @ApiResponse(responseCode = "400", description = "User not in room")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "404", description = "Room or User not found")
    public ResponseEntity<Void> leaveChatRoom(
            @Parameter(description = "ID of the room to leave") @PathVariable Long roomId) {
        User currentUser = getCurrentUser();
        log.info("Received request for user '{}' to leave room ID: {}", currentUser.getUsername(), roomId);
        try {
            chatRoomService.leaveRoom(roomId, currentUser);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            log.warn("Failed to leave room {}: Status={}, Reason={}", roomId, e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Unexpected error leaving room {} for user '{}'", roomId, currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/discoverable")
    @Operation(summary = "Get public rooms the current user can join (not already a member)")
    @ApiResponse(responseCode = "200", description = "List of discoverable chat rooms", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatRoomDto.class)))
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<List<ChatRoomDto>> getDiscoverableRooms() {
        User currentUser = getCurrentUser();
        log.info("Received request for discoverable rooms for user '{}'", currentUser.getUsername());
        try {
            List<ChatRoom> rooms = chatRoomService.findDiscoverableRooms(currentUser);
            List<ChatRoomDto> roomDtos = rooms.stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(roomDtos);
        } catch (Exception e) {
            log.error("Unexpected error fetching discoverable rooms for user '{}'", currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @GetMapping("/{roomId}/presence")
    @Operation(summary = "Get list of usernames currently online in a specific room")
    @ApiResponse(responseCode = "200", description = "List of online usernames")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "403", description = "User not member of the room")
    @ApiResponse(responseCode = "404", description = "Room not found")
    public ResponseEntity<List<String>> getRoomOnlineMembers(
            @Parameter(description = "ID of the chat room", required = true) @PathVariable Long roomId) {

        User currentUser = getCurrentUser();

        if (!chatRoomService.isUserMemberOfRoom(currentUser.getUsername(), chatRoomRepository.findById(roomId).map(ChatRoom::getName).orElse(null))) {
            log.warn("User '{}' attempted to get presence for room ID {} they are not a member of.", currentUser.getUsername(), roomId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }


        log.info("Received request for online members in room ID: {} by user '{}'", roomId, currentUser.getUsername());
        try {
            List<String> onlineMembers = chatRoomService.getOnlineMembers(roomId);
            return ResponseEntity.ok(onlineMembers);
        } catch (ResponseStatusException e) {
            log.warn("Failed to get presence for room {}: Status={}, Reason={}", roomId, e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(null);
        } catch (Exception e) {
            log.error("Unexpected error fetching presence for room {} for user '{}'", roomId, currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/{roomId}/invites")
    @Operation(summary = "Invite a registered user to join a chat room",
            description = "The inviting user must be a member of the room. The invited user must exist and not already be a member.")
    @ApiResponse(responseCode = "200", description = "User invitation process initiated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., self-invite, user already member, pending invite exists)")
    @ApiResponse(responseCode = "401", description = "Inviting user not authenticated")
    @ApiResponse(responseCode = "403", description = "Inviting user is not a member of the room or trying to invite to a room they cannot access")
    @ApiResponse(responseCode = "404", description = "Room or User to invite not found")
    public ResponseEntity<Void> inviteUserToRoom(
            @Parameter(description = "ID of the room to invite to", required = true) @PathVariable Long roomId,
            @Valid @org.springframework.web.bind.annotation.RequestBody
            InviteUserRequest inviteRequest) {

        User invitingUser = getCurrentUser();
        String usernameToInvite = inviteRequest.getUsername();

        log.info("Controller: Received request from user '{}' to invite user '{}' to room ID: {}",
                invitingUser.getUsername(), usernameToInvite, roomId);
        try {
            chatRoomService.inviteUserToRoom(roomId, usernameToInvite, invitingUser);

            log.info("Controller: Invite request for user '{}' to room {} processed successfully by service.", usernameToInvite, roomId);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            log.warn("Controller: Failed to invite user '{}' to room {}: Status={}, Reason={}",
                    usernameToInvite, roomId, e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Controller: Unexpected error inviting user '{}' to room {} by user '{}'",
                    usernameToInvite, roomId, invitingUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}