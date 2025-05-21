package com.chatapp.backend.controller;

import com.chatapp.backend.config.TestControllerConfiguration;
import com.chatapp.backend.model.*;
import com.chatapp.backend.model.dto.RoomInvitationDto;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.RoomInvitationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestControllerConfiguration.class)
@Transactional
class RoomInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomInvitationService roomInvitationService;
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private UserRepository userRepository;

    private User mockCurrentUser;
    private User mockInviter;
    private ChatRoom mockRoom;
    private RoomInvitation pendingInvitation;
    private RoomInvitationDto pendingInvitationDto;

    @BeforeEach
    void setUp() {
        Mockito.reset(roomInvitationService, chatRoomService, userRepository);

        mockCurrentUser = new User();
        mockCurrentUser.setId(1L);
        mockCurrentUser.setUsername("currentUser");

        mockInviter = new User();
        mockInviter.setId(2L);
        mockInviter.setUsername("inviterUser");

        mockRoom = ChatRoom.builder().id(10L).name("Test Invite Room").build();

        pendingInvitation = RoomInvitation.builder()
                .id(100L)
                .room(mockRoom)
                .invitedUser(mockCurrentUser)
                .invitingUser(mockInviter)
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        pendingInvitationDto = RoomInvitationDto.builder()
                .id(pendingInvitation.getId())
                .roomId(mockRoom.getId())
                .roomName(mockRoom.getName())
                .invitedByUsername(mockInviter.getUsername())
                .status(InvitationStatus.PENDING)
                .createdAt(pendingInvitation.getCreatedAt())
                .build();

        given(userRepository.findByUsername("currentUser")).willReturn(Optional.of(mockCurrentUser));
    }

    // Tests for GET /api/invitations/pending
    @Test
    @WithMockUser(username = "currentUser")
    void getPendingInvitations_whenInvitesExist_shouldReturnListOfInvitationDtos() throws Exception {
        // Arrange
        given(roomInvitationService.getPendingInvitationsForUser(any(User.class)))
                .willReturn(List.of(pendingInvitation));

        // Act
        ResultActions response = mockMvc.perform(get("/api/invitations/pending")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        try {
            response.andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(pendingInvitationDto.getId().intValue())))
                    .andExpect(jsonPath("$[0].roomName", is(pendingInvitationDto.getRoomName())))
                    .andExpect(jsonPath("$[0].invitedByUsername", is(pendingInvitationDto.getInvitedByUsername())));
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(roomInvitationService).getPendingInvitationsForUser(any(User.class));
    }

    @Test
    @WithMockUser(username = "currentUser")
    void getPendingInvitations_whenNoInvites_shouldReturnEmptyList() throws Exception {
        given(roomInvitationService.getPendingInvitationsForUser(any(User.class)))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/invitations/pending")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getPendingInvitations_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/invitations/pending"))
                .andExpect(status().isForbidden());
    }

    // Tests for POST /api/invitations/{invitationId}/accept
    @Test
    @WithMockUser(username = "currentUser")
    void acceptInvitation_whenValid_shouldReturnOk() throws Exception {
        Long invitationId = pendingInvitation.getId();

        doNothing().when(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));

        ResultActions response = mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId));

        try {
            response.andExpect(status().isOk());
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));
    }

    @Test
    @WithMockUser(username = "currentUser")
    void acceptInvitation_whenInvitationNotFound_shouldReturnNotFound() throws Exception {
        Long nonExistentId = 999L;
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."))
                .when(chatRoomService).acceptRoomInvitationAndAddUser(eq(nonExistentId), any(User.class));

        mockMvc.perform(post("/api/invitations/{invitationId}/accept", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "anotherUser")
    void acceptInvitation_whenNotForCurrentUser_shouldReturnForbidden() throws Exception {
        Long invitationId = pendingInvitation.getId();
        User anotherTestUser = new User();
        anotherTestUser.setId(3L);
        anotherTestUser.setUsername("anotherUser");
        given(userRepository.findByUsername("anotherUser")).willReturn(Optional.of(anotherTestUser));

        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation is not for you."))
                .when(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));

        mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId))
                .andExpect(status().isForbidden());
    }


    @Test
    void acceptInvitation_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/invitations/{invitationId}/accept", pendingInvitation.getId()))
                .andExpect(status().isForbidden());
    }

    // Tests for POST /api/invitations/{invitationId}/decline
    @Test
    @WithMockUser(username = "currentUser")
    void declineInvitation_whenValid_shouldReturnOk() throws Exception {
        Long invitationId = pendingInvitation.getId();
        doNothing().when(chatRoomService).declineRoomInvitation(eq(invitationId), any(User.class));

        mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId))
                .andExpect(status().isOk());

        verify(chatRoomService).declineRoomInvitation(eq(invitationId), any(User.class));
    }

    @Test
    @WithMockUser(username = "currentUser")
    void declineInvitation_whenInvitationNotFound_shouldReturnNotFound() throws Exception {
        Long nonExistentId = 999L;
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."))
                .when(chatRoomService).declineRoomInvitation(eq(nonExistentId), any(User.class));

        mockMvc.perform(post("/api/invitations/{invitationId}/decline", nonExistentId))
                .andExpect(status().isNotFound());
    }

}