package com.chatapp.backend.controller;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
@ActiveProfiles("test-message-controller")
class MessageControllerTest {

    @TestConfiguration
    @ActiveProfiles("test-message-controller")
    static class MessageControllerTestConfig {

        @Bean
        @Primary
        public MessageRepository mockMessageRepository() {
            return Mockito.mock(MessageRepository.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MessageController messageController;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    private ChatMessage message1;
    private ChatMessage message2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Mockito.reset(messageRepository);

        message1 = new ChatMessage();
        message1.setId(1L);
        message1.setRoomId("room1");
        message1.setSender("user1");
        message1.setContent("Hello");
        message1.setTimestamp(Instant.now().minusSeconds(60));

        message2 = new ChatMessage();
        message2.setId(2L);
        message2.setRoomId("room1");
        message2.setSender("user2");
        message2.setContent("Hi there");
        message2.setTimestamp(Instant.now());


    }

    @Test
    @WithMockUser
    void getMessages_whenMessagesExist_shouldReturnPagedMessages() throws Exception {
        // Arrange
        String roomId = "room1";
        int page = 0;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);
        List<ChatMessage> messages = List.of(message2, message1);
        Page<ChatMessage> messagePage = new PageImpl<>(messages, pageable, messages.size());

        // Mock repository behavior
        given(messageRepository.findByRoomIdOrderByTimestampDesc(eq(roomId), any(Pageable.class)))
                .willReturn(messagePage);

        // Act
        ResultActions response = mockMvc.perform(get("/api/messages")
                .param("roomId", roomId)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is((int)message2.getId().longValue())))
                .andExpect(jsonPath("$.content[1].id", is((int)message1.getId().longValue())))
                .andExpect(jsonPath("$.content[0].content", is("Hi there")))
                .andExpect(jsonPath("$.content[1].content", is("Hello")))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.number", is(page)))
                .andExpect(jsonPath("$.size", is(size)));

        // Verify
        verify(messageRepository, times(1)).findByRoomIdOrderByTimestampDesc(eq(roomId), any(Pageable.class));
    }

    @Test
    @WithMockUser
    void getMessages_whenNoMessagesExist_shouldReturnEmptyPage() throws Exception {
        String roomId = "emptyRoom";
        int page = 0;
        int size = 20;
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> emptyPage = new PageImpl<>(List.of(), pageable, 0); // Empty page

        given(messageRepository.findByRoomIdOrderByTimestampDesc(eq(roomId), any(Pageable.class)))
                .willReturn(emptyPage);

        ResultActions response = mockMvc.perform(get("/api/messages")
                .param("roomId", roomId)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .accept(MediaType.APPLICATION_JSON));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        verify(messageRepository, times(1)).findByRoomIdOrderByTimestampDesc(eq(roomId), any(Pageable.class));
    }

    @Test
    void getMessages_whenNotAuthenticated_shouldReturnUnauthorized() throws Exception {
        String roomId = "room1";

        ResultActions response = mockMvc.perform(get("/api/messages")
                .param("roomId", roomId)
                .accept(MediaType.APPLICATION_JSON));

        response.andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getMessages_whenMissingRoomId_shouldReturnBadRequest() throws Exception {
        ResultActions response = mockMvc.perform(get("/api/messages")
                .accept(MediaType.APPLICATION_JSON));

        response.andExpect(status().isBadRequest());
    }
}