package com.chatapp.backend.controller;

import com.chatapp.backend.config.TestControllerConfiguration;
import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;


import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestControllerConfiguration.class)
@Transactional
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private UserRepository userRepository;

    private User mockUser;
    private ChatRoom room1;
    private ChatRoom room2;

    @BeforeEach
    void setUp() {
        Mockito.reset(chatRoomService, userRepository);

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("mockUser");

        room1 = ChatRoom.builder().id(10L).name("Room 1").createdBy(mockUser).build();
        room2 = ChatRoom.builder().id(11L).name("Room 2").createdBy(mockUser).build();

        given(userRepository.findByUsername(anyString())).willReturn(Optional.of(mockUser));
        given(userRepository.findByUsername("mockUser")).willReturn(Optional.of(mockUser));
    }

    @Test
    @WithMockUser(username = "mockUser")
    void createChatRoom_whenValidRequest_shouldReturnCreated() throws Exception {
        // Arrange
        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName("Room 1");
        given(chatRoomService.createRoom(eq("Room 1"), any(User.class))).willReturn(room1);

        // Act
        ResultActions response = mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Assert
        try {
            response.andExpect(status().isCreated());
        } catch (AssertionError e) {
            System.err.println("Failure in: createChatRoom_whenValidRequest");
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).createRoom(eq("Room 1"), any(User.class));
        verify(userRepository, times(1)).findByUsername("mockUser");
    }

    @Test
    @WithMockUser(username = "mockUser")
    void createChatRoom_whenNameIsInvalid_shouldReturnBadRequest() throws Exception {
        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName("R");

        ResultActions response = mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
        try {
            response.andExpect(status().isBadRequest());
        } catch (AssertionError e) {
            System.err.println("Failure in: createChatRoom_whenNameIsInvalid");
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, never()).createRoom(anyString(), any(User.class));
    }

    @Test
    void createChatRoom_unauthenticated_shouldReturnForbidden() throws Exception {
        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName("Room X");

        ResultActions response = mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        try {
            response.andExpect(status().isForbidden());
        } catch (AssertionError e) {
            System.err.println("Failure in: createChatRoom_unauthenticated");
            response.andDo(print());
            throw e;
        }
    }

    @Test
    @WithMockUser(username = "mockUser")
    void getUserChatRooms_shouldReturnUserRooms() throws Exception {
        List<ChatRoom> userRooms = List.of(room1, room2);
        given(chatRoomService.findRoomsForUser(any(User.class))).willReturn(userRooms);

        ResultActions response = mockMvc.perform(get("/api/rooms"));

        try {
            response.andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].name", is("Room 1")))
                    .andExpect(jsonPath("$[1].name", is("Room 2")));
        } catch (AssertionError e) {
            System.err.println("Failure in: getUserChatRooms");
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).findRoomsForUser(any(User.class));
        verify(userRepository, times(1)).findByUsername("mockUser");
    }

    @Test
    void getUserChatRooms_unauthenticated_shouldReturnForbidden() throws Exception {
        ResultActions response = mockMvc.perform(get("/api/rooms"));
        try {
            response.andExpect(status().isForbidden());
        } catch (AssertionError e) {
            System.err.println("Failure in: getUserChatRooms_unauthenticated");
            response.andDo(print());
            throw e;
        }
    }
}