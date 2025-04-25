package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.ChatRoomDto;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
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

    public ChatRoomController(ChatRoomService chatRoomService, UserRepository userRepository) {
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
                .orElseThrow(() -> {
                    log.error("Authenticated user '{}' not found in database!", username);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found in database");
                });
    }

    @PostMapping
    @Operation(summary = "Create a new chat room")
    @ApiResponse(responseCode = "201", description = "Chat room created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data or room name already exists")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<ChatRoomDto> createChatRoom(@Valid @RequestBody CreateChatRoomRequest request) {
        log.info("Received request to create room: {}", request.getName());
        User currentUser = getCurrentUser();
        try {
            ChatRoom savedRoom = chatRoomService.createRoom(request.getName().trim(), currentUser);
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
    @ApiResponse(responseCode = "200", description = "List of chat rooms")
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
                .build();
    }

    // TODO: Add endpoints for joining/leaving rooms, calling corresponding service methods
}