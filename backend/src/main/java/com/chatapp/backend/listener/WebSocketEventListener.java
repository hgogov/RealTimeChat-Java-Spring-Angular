package com.chatapp.backend.listener;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketEventListener {
    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate,
                                  RedisTemplate<String, String> redisTemplate,
                                  UserRepository userRepository,
                                  ChatRoomRepository chatRoomRepository) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    @EventListener
    @Transactional(readOnly = true)
    public void handleConnect(SessionConnectedEvent event) {
        Principal userPrincipal = event.getUser();
        String username = extractUsername(userPrincipal);

        if (isAnonymous(username)) {
            log.warn("Anonymous user connected via WebSocket event. Ignoring presence.");
            return;
        }

        log.info("User connected via WebSocket event: {}", username);

        // 1. Set user as online in Redis
        redisTemplate.opsForValue().set(getUserOnlineKey(username), "true");

        // 2. Find rooms the user is a member of
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.error("Cannot find user '{}' in repository during connect event!", username);
            return;
        }

        List<ChatRoom> userRooms = chatRoomRepository.findChatRoomsByUserId(user.getId());
        log.debug("User '{}' is member of rooms: {}", username, userRooms.stream().map(ChatRoom::getName).toList());

        // 3. Broadcast ONLINE status to each room the user is in
        Map<String, Object> presenceUpdate = Map.of("username", username, "online", true);
        for (ChatRoom room : userRooms) {
            String destination = getRoomPresenceTopic(room.getName());
            log.info("Broadcasting ONLINE status for user '{}' to destination: {}", username, destination);
            messagingTemplate.convertAndSend(destination, presenceUpdate);
        }
    }

    @EventListener
    @Transactional(readOnly = true)
    public void handleDisconnect(SessionDisconnectEvent event) {
        Principal userPrincipal = event.getUser();
        String username = extractUsername(userPrincipal);

        if (isAnonymous(username)) {
            log.warn("Anonymous user disconnected via WebSocket event. Ignoring presence.");
            return;
        }

        log.info("User disconnected via WebSocket event: {}", username);

        // 1. Remove user from Redis online status
        Boolean deleted = redisTemplate.delete(getUserOnlineKey(username));
        log.debug("Redis key {} deleted: {}", getUserOnlineKey(username), deleted);


        // 2. Find rooms the user was a member of
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("User '{}' not found during disconnect event. Cannot broadcast offline status to rooms.", username);
            return;
        }

        List<ChatRoom> userRooms = chatRoomRepository.findChatRoomsByUserId(user.getId());
        log.debug("Broadcasting OFFLINE for user '{}' to rooms: {}", username, userRooms.stream().map(ChatRoom::getName).toList());


        // 3. Broadcast OFFLINE status to each room the user was in
        Map<String, Object> presenceUpdate = Map.of("username", username, "online", false);
        for (ChatRoom room : userRooms) {
            String destination = getRoomPresenceTopic(room.getName());
            log.info("Broadcasting OFFLINE status for user '{}' to destination: {}", username, destination);
            messagingTemplate.convertAndSend(destination, presenceUpdate);
        }
    }

    // --- Helper Methods ---

    private boolean isAnonymous(String username) {
        return "anonymous".equals(username) || username == null;
    }

    private String getUserOnlineKey(String username) {
        return "user:" + username + ":online";
    }

    private String getRoomPresenceTopic(String roomNameOrId) {
        return "/topic/presence/" + roomNameOrId;
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