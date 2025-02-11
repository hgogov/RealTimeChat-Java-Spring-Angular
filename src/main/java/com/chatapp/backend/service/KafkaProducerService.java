package com.chatapp.backend.service;

import com.chatapp.backend.model.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Message> kafkaTemplate;
    private final String topic;

    public KafkaProducerService(KafkaTemplate<String, Message> kafkaTemplate, @Value("${kafka.topics.chat-messages}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendMessage(Message message) {
        kafkaTemplate.send(topic, message);
    }
}
