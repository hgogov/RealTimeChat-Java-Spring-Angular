package com.chatapp.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        // Add JSON message converter
        messageConverters.add(new MappingJackson2MessageConverter());
        return false; // Do not add default converters
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker
        config.enableSimpleBroker("/topic"); // Prefix for subscribing to topics
        config.setApplicationDestinationPrefixes("/app"); // Prefix for sending messages
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the WebSocket endpoint
        registry.addEndpoint("/chat-websocket")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // Fallback for browsers that don't support WebSocket
    }
}
