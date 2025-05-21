package com.chatapp.backend.config;

import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.MessageRepository;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.CustomUserDetailsService;
import com.chatapp.backend.service.RoomInvitationService;
import com.chatapp.backend.utils.JwtUtils;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyString;

@TestConfiguration
public class TestControllerConfiguration {

    @Bean
    @Primary
    public UserRepository mockUserRepository() {
        System.out.println("--- Providing Mock UserRepository via TestConfig ---");
        return Mockito.mock(UserRepository.class);
    }

    @Bean
    @Primary
    public PasswordEncoder mockPasswordEncoder() {
        System.out.println("--- Providing Mock PasswordEncoder via TestConfig ---");
        return Mockito.mock(PasswordEncoder.class);
    }

    @Bean
    @Primary
    public CustomUserDetailsService mockCustomUserDetailsService() {
        System.out.println("--- Providing Mock CustomUserDetailsService via TestConfig ---");
        return Mockito.mock(CustomUserDetailsService.class);
    }

    @Bean
    @Primary
    public AuthenticationManager mockAuthenticationManager() {
        System.out.println("--- Providing Mock AuthenticationManager via TestConfig ---");
        return Mockito.mock(AuthenticationManager.class);
    }

    @Bean
    @Primary
    public JwtUtils mockJwtUtils() {
        System.out.println("--- Providing Mock JwtUtils via TestConfig ---");
        return Mockito.mock(JwtUtils.class);
    }

    @Bean
    @Primary
    public ChatRoomService mockChatRoomService() {
        System.out.println("--- Providing Mock ChatRoomService via TestConfig ---");
        return Mockito.mock(ChatRoomService.class);
    }

    @Bean
    @Primary
    public ChatRoomRepository mockChatRoomRepository() {
        System.out.println("--- Providing Mock ChatRoomRepository via TestConfig ---");
        return Mockito.mock(ChatRoomRepository.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, String> mockRedisTemplate() {
        System.out.println("--- Providing Mock RedisTemplate via TestConfig ---");
        RedisTemplate<String, String> redisTemplateMock = Mockito.mock(RedisTemplate.class);

        ValueOperations<String, String> valueOperationsMock = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplateMock.opsForValue()).thenReturn(valueOperationsMock);

        Mockito.when(redisTemplateMock.hasKey(anyString())).thenReturn(false);

        Mockito.when(redisTemplateMock.delete(anyString())).thenReturn(true);

        return redisTemplateMock;
    }

    @Bean
    @Primary
    public MessageRepository mockMessageRepository() {
        System.out.println("--- Providing Mock MessageRepository via TestConfig ---");
        return Mockito.mock(MessageRepository.class);
    }

    @Bean
    @Primary
    public SimpMessagingTemplate mockSimpMessagingTemplate() {
        System.out.println("--- Providing Mock SimpMessagingTemplate via TestConfig ---");
        return Mockito.mock(SimpMessagingTemplate.class);
    }

    @Bean
    @Primary
    public RoomInvitationService mockRoomInvitationService() {
        System.out.println("--- Providing Mock RoomInvitationService via TestConfig ---");
        return Mockito.mock(RoomInvitationService.class);
    }
}