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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName("Room 1");
        request.setIsPublic(true);

        given(chatRoomService.createRoom(
                argThat(req -> req.getName().equals("Room 1") && req.getIsPublic() == true),
                any(User.class)
        )).willReturn(room1);

        ResultActions response = mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        try {
            response.andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(10)))
                    .andExpect(jsonPath("$.name", is("Room 1")));
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).createRoom(
                argThat(req -> req.getName().equals("Room 1") && req.getIsPublic() == true),
                any(User.class)
        );
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

        verify(chatRoomService, never()).createRoom(any(), any(User.class));
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

    // Tests for POST /{roomId}/join
    @Test
    @WithMockUser(username = "mockUser")
    void joinChatRoom_whenSuccessful_shouldReturnOk() throws Exception {
        Long roomIdToJoin = room2.getId();

        doNothing().when(chatRoomService).joinRoom(eq(roomIdToJoin), any(User.class));

        ResultActions response = mockMvc.perform(post("/api/rooms/{roomId}/join", roomIdToJoin));

        try {
            response.andExpect(status().isOk());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).joinRoom(eq(roomIdToJoin), any(User.class));
        verify(userRepository, times(1)).findByUsername("mockUser"); // Verify getCurrentUser worked
    }

    @Test
    @WithMockUser(username = "mockUser")
    void joinChatRoom_whenRoomNotFound_shouldReturnNotFound() throws Exception {
        Long nonExistentRoomId = 99L;

        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"))
                .when(chatRoomService).joinRoom(eq(nonExistentRoomId), any(User.class));

        ResultActions response = mockMvc.perform(post("/api/rooms/{roomId}/join", nonExistentRoomId));

        try {
            response.andExpect(status().isNotFound());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).joinRoom(eq(nonExistentRoomId), any(User.class));
    }

    @Test
    @WithMockUser(username = "mockUser")
    void joinChatRoom_whenAlreadyMember_shouldReturnBadRequest() throws Exception {
        Long existingRoomId = room1.getId();
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already in room"))
                .when(chatRoomService).joinRoom(eq(existingRoomId), any(User.class));

        ResultActions response = mockMvc.perform(post("/api/rooms/{roomId}/join", existingRoomId));

        try {
            response.andExpect(status().isBadRequest());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).joinRoom(eq(existingRoomId), any(User.class));
    }


    @Test
    void joinChatRoom_whenNotAuthenticated_shouldReturnForbidden() throws Exception { // Changed expectation
        Long roomId = room1.getId();

        ResultActions response = mockMvc.perform(post("/api/rooms/{roomId}/join", roomId));

        try {
            response.andExpect(status().isForbidden());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, never()).joinRoom(anyLong(), any(User.class));
    }

    // Tests for DELETE /{roomId}/leave

    @Test
    @WithMockUser(username = "mockUser")
    void leaveChatRoom_whenSuccessful_shouldReturnOk() throws Exception {
        Long roomIdToLeave = room1.getId();
        doNothing().when(chatRoomService).leaveRoom(eq(roomIdToLeave), any(User.class));

        ResultActions response = mockMvc.perform(delete("/api/rooms/{roomId}/leave", roomIdToLeave));

        try {
            response.andExpect(status().isOk());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).leaveRoom(eq(roomIdToLeave), any(User.class));
        verify(userRepository, times(1)).findByUsername("mockUser");
    }

    @Test
    @WithMockUser(username = "mockUser")
    void leaveChatRoom_whenRoomNotFound_shouldReturnNotFound() throws Exception {
        Long nonExistentRoomId = 99L;
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"))
                .when(chatRoomService).leaveRoom(eq(nonExistentRoomId), any(User.class));

        ResultActions response = mockMvc.perform(delete("/api/rooms/{roomId}/leave", nonExistentRoomId));

        try {
            response.andExpect(status().isNotFound());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).leaveRoom(eq(nonExistentRoomId), any(User.class));
    }

    @Test
    @WithMockUser(username = "mockUser")
    void leaveChatRoom_whenNotMember_shouldReturnBadRequest() throws Exception {
        Long roomNotMemberOfId = room2.getId();
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not in room"))
                .when(chatRoomService).leaveRoom(eq(roomNotMemberOfId), any(User.class));

        ResultActions response = mockMvc.perform(delete("/api/rooms/{roomId}/leave", roomNotMemberOfId));

        try {
            response.andExpect(status().isBadRequest());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).leaveRoom(eq(roomNotMemberOfId), any(User.class));
    }

    @Test
    void leaveChatRoom_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        Long roomId = room1.getId();

        ResultActions response = mockMvc.perform(delete("/api/rooms/{roomId}/leave", roomId));

        try {
            response.andExpect(status().isForbidden());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, never()).leaveRoom(anyLong(), any(User.class));
    }

    // Tests for GET /discoverable

    @Test
    @WithMockUser(username = "mockUser")
    void getDiscoverableRooms_whenAuthenticated_shouldReturnDiscoverableRoomList() throws Exception {
        ChatRoom discoverableRoomA = ChatRoom.builder().id(30L).name("Disco Room A").isPublic(true).createdAt(Instant.now()).build();
        ChatRoom discoverableRoomB = ChatRoom.builder().id(31L).name("Disco Room B").isPublic(true).createdAt(Instant.now()).build();
        List<ChatRoom> serviceResponse = List.of(discoverableRoomA, discoverableRoomB);

        given(chatRoomService.findDiscoverableRooms(any(User.class))).willReturn(serviceResponse);

        ResultActions response = mockMvc.perform(get("/api/rooms/discoverable")
                .accept(MediaType.APPLICATION_JSON));

        try {
            response.andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(30)))
                    .andExpect(jsonPath("$[0].name", is("Disco Room A")))
                    .andExpect(jsonPath("$[0].public", is(true)))
                    .andExpect(jsonPath("$[1].id", is(31)))
                    .andExpect(jsonPath("$[1].name", is("Disco Room B")))
                    .andExpect(jsonPath("$[1].public", is(true)));
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, times(1)).findDiscoverableRooms(any(User.class));
        verify(userRepository, times(1)).findByUsername("mockUser");
    }

    @Test
    void getDiscoverableRooms_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        ResultActions response = mockMvc.perform(get("/api/rooms/discoverable")
                .accept(MediaType.APPLICATION_JSON));

        try {
            response.andExpect(status().isForbidden());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService, never()).findDiscoverableRooms(any(User.class));
    }
}