package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public ChatController(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public void sendMessage(ChatMessage message) {
        logger.info("Received message: {}", message);

        // Send message to Kafka for processing
        kafkaProducerService.sendMessage(message);
    }
}
