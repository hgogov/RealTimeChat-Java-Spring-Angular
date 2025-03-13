package com.chatapp.backend.config;

import com.chatapp.backend.model.ChatMessage;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatMessage> kafkaListenerContainerFactory(ConsumerFactory<String, ChatMessage> consumerFactory, KafkaTemplate<String, ChatMessage> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, ChatMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE); // Manual acknowledgment
        factory.setCommonErrorHandler(defaultErrorHandler(kafkaTemplate));
        return factory;
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, ChatMessage> kafkaTemplate) {
        // Retry 3 times with a 1-second delay between attempts
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        // Configure DLQ publishing to "chat-messages-dlt"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) -> new TopicPartition("chat-messages-dlt", record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class); // Example of a non-retryable exception
        return errorHandler;
    }
}