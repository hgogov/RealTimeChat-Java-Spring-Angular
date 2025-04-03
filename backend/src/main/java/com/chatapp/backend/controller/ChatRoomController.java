package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.ChatRoomDto;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    public ChatRoomController(ChatRoomRepository chatRoomRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
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

    @PostMapping
    @Operation(summary = "Create a new chat room")
    @ApiResponse(responseCode = "201", description = "Chat room created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data or room name already exists")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<ChatRoomDto> createChatRoom(@Valid @RequestBody CreateChatRoomRequest request) {
        User currentUser = getCurrentUser();

        // Check if room name already exists
        if (chatRoomRepository.findByName(request.getName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name '" + request.getName() + "' already exists.");
        }

        ChatRoom newRoom = ChatRoom.builder()
                .name(request.getName())
                .createdBy(currentUser)
                .build();

        // Add the creator as the first member
        newRoom.getMembers().add(currentUser);
        currentUser.getChatRooms().add(newRoom);

        ChatRoom savedRoom = chatRoomRepository.save(newRoom);
        userRepository.save(currentUser);

        return new ResponseEntity<>(mapToDto(savedRoom), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get chat rooms the current user is a member of")
    @ApiResponse(responseCode = "200", description = "List of chat rooms")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    public ResponseEntity<List<ChatRoomDto>> getUserChatRooms() {
        User currentUser = getCurrentUser();

        List<ChatRoom> rooms = chatRoomRepository.findChatRoomsByUserId(currentUser.getId());

        List<ChatRoomDto> roomDtos = rooms.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(roomDtos);
    }

    private ChatRoomDto mapToDto(ChatRoom room) {
        return ChatRoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .createdByUsername(room.getCreatedBy() != null ? room.getCreatedBy().getUsername() : null)
                .createdAt(room.getCreatedAt())
                .build();
    }

    // TODO: Add endpoints for joining existing rooms, leaving rooms, getting room details by ID etc.
}