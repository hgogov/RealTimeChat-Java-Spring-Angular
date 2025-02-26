package com.chatapp.backend.service;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.repository.MessageRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public KafkaConsumerService(MessageRepository messageRepository, SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "${kafka.topics.chat-messages}", groupId = "chat-backend-group")
    public void consumeMessage(ChatMessage message, Acknowledgment acknowledgment) {
        try {
            // Log the consumed message
            logger.info("[KafkaConsumerService] Consumed message: {}", message);

            // Save to DB
            ChatMessage savedMessage = messageRepository.save(message);

            // Log the saved message
            logger.info("[KafkaConsumerService] Saved message to DB: {}", savedMessage);

            // Broadcast to WebSocket subscribers
            messagingTemplate.convertAndSend("/topic/chat/" + savedMessage.getRoomId(), savedMessage);

            // Acknowledge message after successful processing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("[KafkaConsumerService] Error processing message: {}", message, e);
            // Do not acknowledge the message, so Kafka retries it
        }
    }

}
