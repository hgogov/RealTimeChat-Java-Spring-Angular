package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.repository.MessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages", description = "Retrieve Chat Message History")
public class MessageController {
    private final MessageRepository messageRepository;

    public MessageController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Operation(summary = "Get message history for a specific room")
    @GetMapping
    public ResponseEntity<Page<ChatMessage>> getMessages(
            @Parameter(description = "ID of the chat room", required = true) @RequestParam String roomId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of messages per page") @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                messageRepository.findByRoomIdOrderByTimestampDesc(
                        roomId,
                        PageRequest.of(page, size)
                )
        );
    }
}
