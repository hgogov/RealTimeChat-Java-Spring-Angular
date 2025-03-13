package com.chatapp.backend.service;

import com.chatapp.backend.model.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;
    private final String topic;

    public KafkaProducerService(KafkaTemplate<String, ChatMessage> kafkaTemplate, @Value("${kafka.topics.chat-messages}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendMessage(ChatMessage message) {
        logger.info("[KafkaProducerService] Sending message to Kafka: {}", message);
        kafkaTemplate.send(topic, message);
    }
}
