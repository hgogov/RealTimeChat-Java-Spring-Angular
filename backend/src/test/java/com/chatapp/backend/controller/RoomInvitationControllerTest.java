package com.chatapp.backend.controller;

import com.chatapp.backend.config.TestControllerConfiguration;
import com.chatapp.backend.model.*;
import com.chatapp.backend.model.dto.RoomInvitationDto;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.RoomInvitationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("RoomInvitationController Tests")
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
    private RoomInvitation pendingInvitationEntity;
    private RoomInvitationDto pendingInvitationDto;
    private final String MOCK_CURRENT_USER_USERNAME = "testCurrentUser";

    @BeforeEach
    void setUp() {
        Mockito.reset(roomInvitationService, chatRoomService, userRepository);

        mockCurrentUser = new User();
        mockCurrentUser.setId(1L);
        mockCurrentUser.setUsername(MOCK_CURRENT_USER_USERNAME);

        mockInviter = new User();
        mockInviter.setId(2L);
        mockInviter.setUsername("inviterUser");

        mockRoom = ChatRoom.builder().id(10L).name("Test Invite Room").build();

        pendingInvitationEntity = RoomInvitation.builder()
                .id(100L)
                .room(mockRoom)
                .invitedUser(mockCurrentUser)
                .invitingUser(mockInviter)
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        pendingInvitationDto = RoomInvitationDto.builder()
                .id(pendingInvitationEntity.getId())
                .roomId(mockRoom.getId())
                .roomName(mockRoom.getName())
                .invitedByUsername(mockInviter.getUsername())
                .status(InvitationStatus.PENDING)
                .createdAt(pendingInvitationEntity.getCreatedAt())
                .build();

        given(userRepository.findByUsername(MOCK_CURRENT_USER_USERNAME)).willReturn(Optional.of(mockCurrentUser));
    }

    @Nested
    @DisplayName("GET /api/invitations/pending")
    class GetPendingInvitationsTests {
        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return list of pending invitations when invites exist")
        void getPendingInvitations_whenInvitesExist_shouldReturnListOfInvitationDtos() throws Exception {
            given(roomInvitationService.getPendingInvitationsForUser(any(User.class)))
                    .willReturn(List.of(pendingInvitationEntity));

            ResultActions response = mockMvc.perform(get("/api/invitations/pending")
                    .accept(MediaType.APPLICATION_JSON));

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

            verify(userRepository).findByUsername(MOCK_CURRENT_USER_USERNAME);
            verify(roomInvitationService).getPendingInvitationsForUser(any(User.class));
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return empty list when no pending invites exist")
        void getPendingInvitations_whenNoInvites_shouldReturnEmptyList() throws Exception {
            given(roomInvitationService.getPendingInvitationsForUser(any(User.class)))
                    .willReturn(Collections.emptyList());

            mockMvc.perform(get("/api/invitations/pending").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return Forbidden when not authenticated")
        void getPendingInvitations_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
            mockMvc.perform(get("/api/invitations/pending"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/invitations/{invitationId}/accept")
    class AcceptInvitationTests {
        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return OK when invitation is valid and accepted")
        void acceptInvitation_whenValid_shouldReturnOk() throws Exception {
            Long invitationId = pendingInvitationEntity.getId();
            doNothing().when(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId))
                    .andExpect(status().isOk());

            verify(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return Not Found when invitation does not exist")
        void acceptInvitation_whenInvitationNotFound_shouldReturnNotFound() throws Exception {
            Long nonExistentId = 999L;
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."))
                    .when(chatRoomService).acceptRoomInvitationAndAddUser(eq(nonExistentId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", nonExistentId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return Forbidden when invitation is not for current user")
        void acceptInvitation_whenNotForCurrentUser_shouldReturnForbidden() throws Exception {
            Long invitationId = pendingInvitationEntity.getId();

            doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation is not for you."))
                    .when(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));


            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return Bad Request when invitation is not pending")
        void acceptInvitation_whenNotPending_shouldReturnBadRequest() throws Exception {
            Long invitationId = pendingInvitationEntity.getId();
            doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is no longer pending."))
                    .when(chatRoomService).acceptRoomInvitationAndAddUser(eq(invitationId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/accept", invitationId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return Forbidden when not authenticated")
        void acceptInvitation_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/accept", pendingInvitationEntity.getId()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/invitations/{invitationId}/decline")
    class DeclineInvitationTests {
        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return OK when invitation is valid and declined")
        void declineInvitation_whenValid_shouldReturnOk() throws Exception {
            Long invitationId = pendingInvitationEntity.getId();
            doNothing().when(chatRoomService).declineRoomInvitation(eq(invitationId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId))
                    .andExpect(status().isOk());

            verify(chatRoomService).declineRoomInvitation(eq(invitationId), any(User.class));
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return Not Found when invitation does not exist")
        void declineInvitation_whenInvitationNotFound_shouldReturnNotFound() throws Exception {
            Long nonExistentId = 999L;
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."))
                    .when(chatRoomService).declineRoomInvitation(eq(nonExistentId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", nonExistentId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return Forbidden when invitation is not for current user")
        void declineInvitation_whenNotForCurrentUser_shouldReturnForbidden() throws Exception {
            Long invitationId = pendingInvitationEntity.getId();
            doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation is not for you."))
                    .when(chatRoomService).declineRoomInvitation(eq(invitationId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = MOCK_CURRENT_USER_USERNAME)
        @DisplayName("Should return Bad Request when invitation is not pending")
        void declineInvitation_whenNotPending_shouldReturnBadRequest() throws Exception {
            Long invitationId = pendingInvitationEntity.getId();
            doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is no longer pending."))
                    .when(chatRoomService).declineRoomInvitation(eq(invitationId), any(User.class));

            mockMvc.perform(post("/api/invitations/{invitationId}/decline", invitationId))
                    .andExpect(status().isBadRequest());
        }


        @Test
        @DisplayName("Should return Forbidden when not authenticated")
        void declineInvitation_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
            mockMvc.perform(post("/api/invitations/{invitationId}/decline", pendingInvitationEntity.getId()))
                    .andExpect(status().isForbidden());
        }
    }
}