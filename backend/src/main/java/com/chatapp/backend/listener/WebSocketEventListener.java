package com.chatapp.backend.listener;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Component
public class WebSocketEventListener {
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate,
                                  RedisTemplate<String, String> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        String username = extractUsername(event.getUser());
        redisTemplate.opsForValue().set("user:" + username + ":online", "true");
        broadcastPresence(username, true);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String username = extractUsername(event.getUser());
        redisTemplate.delete("user:" + username + ":online");
        broadcastPresence(username, false);
    }

    private void broadcastPresence(String username, boolean isOnline) {
        messagingTemplate.convertAndSend("/topic/presence",
                Map.of("username", username, "status", isOnline ? "online" : "offline")
        );
    }

    private String extractUsername(Principal principal) {
        return principal != null ? principal.getName() : "anonymous";
    }
}
