package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.model.TypingEvent;
import com.chatapp.backend.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final KafkaProducerService kafkaProducerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public ChatController(KafkaProducerService kafkaProducerService, SimpMessagingTemplate messagingTemplate, RedisTemplate<String, String> redisTemplate) {
        this.kafkaProducerService = kafkaProducerService;
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage message) {
        logger.info("Received message: {}", message);
        // Send message to Kafka for processing (consumer will broadcast via /topic/chat/{roomId})
        kafkaProducerService.sendMessage(message);
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingEvent typingEvent, SimpMessageHeaderAccessor headerAccessor) {
        String username = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "unknown";
        logger.info("Received typing event: {} from user: {}", typingEvent, username);

        // Broadcast the typing event to others in the room
        messagingTemplate.convertAndSend("/topic/typing/" + typingEvent.getRoomId(), typingEvent);
    }

    @MessageMapping("/presence.requestList")
    public void handlePresenceListRequest(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();
        String username = (user != null) ? user.getName() : "unknown";

        System.out.println("Received presence list request from user: " + username + " (Session: " + sessionId + ")");

        Set<String> onlineUserKeys = redisTemplate.keys("user:*:online");
        Set<Map<String, Object>> onlineUsersList = Set.of();

        if (onlineUserKeys != null && !onlineUserKeys.isEmpty()) {
            onlineUsersList = onlineUserKeys.stream()
                    .map(key -> key.substring("user:".length(), key.indexOf(":online")))
                    .map(name -> Map.<String, Object>of("username", name, "online", true))
                    .collect(Collectors.toSet());
        }

        System.out.println("Sending presence list back via /topic/presence.list: " + onlineUsersList);
        // Send the list to a public topic that all clients subscribe to
        messagingTemplate.convertAndSend("/topic/presence.list", onlineUsersList);
    }
}
