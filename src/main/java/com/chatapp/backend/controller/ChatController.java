package com.chatapp.backend.controller;

import com.chatapp.backend.model.Message;
import com.chatapp.backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Message message) {
        // Save the message to the database
        messageRepository.save(message);

        // Broadcast the message to the chat room
        messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), message);
    }
}
