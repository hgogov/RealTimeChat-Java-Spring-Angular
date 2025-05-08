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
        // Send message to Kafka for processing
        kafkaProducerService.sendMessage(message);
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingEvent typingEvent, SimpMessageHeaderAccessor headerAccessor) {
        String username = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "unknown";
        if (typingEvent.getRoomId() == null || typingEvent.getRoomId().isEmpty()) {
            logger.warn("Received typing event without roomId from {}: {}", username, typingEvent);
            return;
        }
        logger.info("Received typing event: {} from user: {}", typingEvent, username);

        // Broadcast the typing event to others in the specific room
        String destination = "/topic/typing/" + typingEvent.getRoomId();
        logger.info("Broadcasting typing event to destination: {}", destination);
        messagingTemplate.convertAndSend(destination, typingEvent);
    }
}
