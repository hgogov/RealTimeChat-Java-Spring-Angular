package com.chatapp.backend.listener;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
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
        Principal userPrincipal = event.getUser();
        String username = extractUsername(userPrincipal);
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        System.out.println("User connected: " + username + " (Session: " + sessionId + ")");

        if (!"anonymous".equals(username) && sessionId != null) {
            // Set current user as online in Redis
            redisTemplate.opsForValue().set("user:" + username + ":online", "true");

            // Broadcast the connection event to everyone ELSE
            broadcastPresence(username, true);

        } else {
            System.err.println("!!! Warning: Anonymous user connected or missing session ID, cannot set presence.");
        }
    }


    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        Principal userPrincipal = event.getUser();
        String username = extractUsername(userPrincipal);

        System.out.println("User disconnected: " + username);

        if (!"anonymous".equals(username)) {
            redisTemplate.delete("user:" + username + ":online");
            broadcastPresence(username, false);
        } else {
            System.err.println("!!! Warning: Anonymous user disconnected.");
        }
    }

    // Broadcast individual presence change to the general topic
    private void broadcastPresence(String username, boolean isOnline) {
        System.out.println("Broadcasting presence update for " + username + " to /topic/presence");
        Map<String, Object> presenceUpdate = Map.of("username", username, "online", isOnline);
        messagingTemplate.convertAndSend("/topic/presence", presenceUpdate);
    }

    private String extractUsername(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            Object principalObject = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (principalObject instanceof UserDetails) {
                return ((UserDetails) principalObject).getUsername();
            } else if (principalObject instanceof String) {
                return (String) principalObject;
            }
        } else if (principal != null) {
            return principal.getName();
        }
        return "anonymous";
    }
}