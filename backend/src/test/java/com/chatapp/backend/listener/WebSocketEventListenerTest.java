package com.chatapp.backend.listener;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private WebSocketEventListener listener;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<Object> payloadCaptor;

    private final String username = "testUser";
    private final String onlineKey = "user:testUser:online";

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Principal createPrincipal(String name) {
        return new UsernamePasswordAuthenticationToken(name, null);
    }

    @Test
    @DisplayName("Anonymous user on connect is ignored")
    void testConnectAnonymousIgnored() {
        Principal anon = createPrincipal(null);
        SessionConnectedEvent event = mock(SessionConnectedEvent.class);
        when(event.getUser()).thenReturn(anon);

        listener.handleConnect(event);

        verifyNoInteractions(valueOperations, userRepository, chatRoomRepository, messagingTemplate);
    }

    @Test
    @DisplayName("Handle connect: user online set and presence broadcast")
    void testHandleConnectBroadcastsOnline() {
        Principal userPrincipal = createPrincipal(username);
        SessionConnectedEvent event = mock(SessionConnectedEvent.class);
        when(event.getUser()).thenReturn(userPrincipal);

        User user = new User();
        user.setId(42L);
        user.setUsername(username);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        ChatRoom room1 = new ChatRoom();
        room1.setName("roomA");
        ChatRoom room2 = new ChatRoom();
        room2.setName("roomB");
        when(chatRoomRepository.findChatRoomsByUserId(42L)).thenReturn(List.of(room1, room2));

        listener.handleConnect(event);

        verify(valueOperations).set(eq(onlineKey), eq("true"));

        Map<String, Object> expectedPayload = Map.of("username", username, "online", true);
        verify(messagingTemplate).convertAndSend("/topic/presence/roomA", expectedPayload);
        verify(messagingTemplate).convertAndSend("/topic/presence/roomB", expectedPayload);
    }

    @Test
    @DisplayName("Handle connect: missing user in repository")
    void testHandleConnectMissingUser() {
        Principal userPrincipal = createPrincipal(username);
        SessionConnectedEvent event = mock(SessionConnectedEvent.class);
        when(event.getUser()).thenReturn(userPrincipal);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        listener.handleConnect(event);

        verify(valueOperations).set(eq(onlineKey), eq("true"));

        verify(chatRoomRepository, never()).findChatRoomsByUserId(anyLong());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Anonymous user on disconnect is ignored")
    void testDisconnectAnonymousIgnored() {
        Principal anon = createPrincipal(null);
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getUser()).thenReturn(anon);

        listener.handleDisconnect(event);

        verifyNoInteractions(redisTemplate, userRepository, chatRoomRepository, messagingTemplate);
    }

    @Test
    @DisplayName("Handle disconnect: user offline delete and presence broadcast")
    void testHandleDisconnectBroadcastsOffline() {
        Principal userPrincipal = createPrincipal(username);
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getUser()).thenReturn(userPrincipal);

        User user = new User();
        user.setId(24L);
        user.setUsername(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        ChatRoom room1 = new ChatRoom();
        room1.setName("roomX");
        when(chatRoomRepository.findChatRoomsByUserId(24L)).thenReturn(List.of(room1));

        when(redisTemplate.delete(onlineKey)).thenReturn(true);

        listener.handleDisconnect(event);

        verify(redisTemplate).delete(eq(onlineKey));

        Map<String, Object> expectedPayload = Map.of("username", username, "online", false);
        verify(messagingTemplate).convertAndSend("/topic/presence/roomX", expectedPayload);
    }

    @Test
    @DisplayName("Handle disconnect: missing user in repository")
    void testHandleDisconnectMissingUser() {
        Principal userPrincipal = createPrincipal(username);
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getUser()).thenReturn(userPrincipal);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        listener.handleDisconnect(event);

        verify(redisTemplate).delete(eq(onlineKey));
        verify(chatRoomRepository, never()).findChatRoomsByUserId(anyLong());
        verifyNoInteractions(messagingTemplate);
    }
}
