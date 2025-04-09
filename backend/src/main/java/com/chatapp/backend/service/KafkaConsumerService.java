package com.chatapp.backend.service;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.repository.MessageRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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
            logger.info("[KafkaConsumerService] Consumed message for room '{}': {}", message.getRoomId(), message);

            ChatMessage savedMessage = messageRepository.save(message);
            logger.info("[KafkaConsumerService] Saved message to DB: {}", savedMessage);

            // Broadcast to WebSocket subscribers FOR THE SPECIFIC ROOM
            String destination = "/topic/chat/" + savedMessage.getRoomId();
            logger.info("[KafkaConsumerService] Broadcasting message to destination: {}", destination);
            messagingTemplate.convertAndSend(destination, savedMessage);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("[KafkaConsumerService] Error processing message: {}", message, e);
            acknowledgment.nack(Duration.ofDays(1000));
        }
    }

    @KafkaListener(topics = "${kafka.topics.chat-messages}-dlt", groupId = "chat-backend-dlt-group")
    public void consumeDeadLetterMessage(ChatMessage message, Acknowledgment acknowledgment) {
        // Handle messages that failed processing permanently
        logger.error("[DLT Consumer] Received dead-letter message: {}", message);
        // TODO: Implement alerting or manual intervention logic here
        acknowledgment.acknowledge(); // Acknowledge DLT message
    }

}
