package com.chatapp.backend.service;

import com.chatapp.backend.model.Message;
import com.chatapp.backend.repository.MessageRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public KafkaConsumerService(MessageRepository messageRepository, SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "${kafka.topics.chat-messages}")
    public void consumeMessage(Message message) {
        // Save to DB
        Message savedMessage = messageRepository.save(message);

        // Broadcast to WebSocket subscribers
        messagingTemplate.convertAndSend("/topic/chat/" + savedMessage.getRoomId(), savedMessage);
    }

}
