package com.chatapp.backend.controller;

import com.chatapp.backend.model.Message;
import com.chatapp.backend.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public ChatController(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Message message) {
        System.out.println("Received message: " + message);

        // Send message to Kafka for processing
        kafkaProducerService.sendMessage(message);
    }
}
