package com.chatapp.backend.service;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private User testUser;
    private ChatRoom testRoom;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testRoom = ChatRoom.builder()
                .id(10L)
                .name("Test Room")
                .createdBy(testUser)
                .build();
        testRoom.getMembers().add(testUser);
    }

    @Test
    void isUserMemberOfRoom_whenMember_shouldReturnTrue() {
        // Arrange
        when(chatRoomRepository.findByName("Test Room")).thenReturn(Optional.of(testRoom));
        when(userRepository.existsByUsernameAndChatRooms_Id("testuser", 10L)).thenReturn(true);

        // Act
        boolean isMember = chatRoomService.isUserMemberOfRoom("testuser", "Test Room");

        // Assert
        assertTrue(isMember);
        // Verify
        verify(chatRoomRepository).findByName("Test Room");
        verify(userRepository).existsByUsernameAndChatRooms_Id("testuser", 10L);
    }

    @Test
    void isUserMemberOfRoom_whenNotMember_shouldReturnFalse() {
        when(chatRoomRepository.findByName("Test Room")).thenReturn(Optional.of(testRoom));
        when(userRepository.existsByUsernameAndChatRooms_Id("anotheruser", 10L)).thenReturn(false);

        boolean isMember = chatRoomService.isUserMemberOfRoom("anotheruser", "Test Room");

        assertFalse(isMember);
        verify(userRepository).existsByUsernameAndChatRooms_Id("anotheruser", 10L);
    }

    @Test
    void isUserMemberOfRoom_whenRoomNotFound_shouldReturnFalse() {
        when(chatRoomRepository.findByName("NonExistentRoom")).thenReturn(Optional.empty());

        boolean isMember = chatRoomService.isUserMemberOfRoom("testuser", "NonExistentRoom");

        assertFalse(isMember);
        verify(chatRoomRepository).findByName("NonExistentRoom");
        verify(userRepository, never()).existsByUsernameAndChatRooms_Id(anyString(), anyLong());
    }


    @Test
    void createRoom_whenNameAvailable_shouldCreateAndReturnRoom() {
        when(chatRoomRepository.findByName("New Room")).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom roomArg = invocation.getArgument(0);
            roomArg.setId(11L);
            return roomArg;
        });
        when(userRepository.save(any(User.class))).thenReturn(testUser);


        ChatRoom createdRoom = chatRoomService.createRoom("New Room", testUser);

        assertNotNull(createdRoom);
        assertEquals("New Room", createdRoom.getName());
        assertEquals(testUser, createdRoom.getCreatedBy());
        assertNotNull(createdRoom.getId());
        assertTrue(createdRoom.getMembers().contains(testUser));

        verify(chatRoomRepository).findByName("New Room");
        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(userRepository).save(testUser);
    }

    @Test
    void createRoom_whenNameExists_shouldThrowException() {
        when(chatRoomRepository.findByName("Existing Room")).thenReturn(Optional.of(testRoom));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.createRoom("Existing Room", testUser);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("already exists"));

        verify(chatRoomRepository).findByName("Existing Room");
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findRoomsForUser_shouldReturnRoomsFromRepository() {
        ChatRoom room1 = ChatRoom.builder().id(1L).name("Room 1").build();
        ChatRoom room2 = ChatRoom.builder().id(2L).name("Room 2").build();
        List<ChatRoom> expectedRooms = List.of(room1, room2);
        when(chatRoomRepository.findChatRoomsByUserId(testUser.getId())).thenReturn(expectedRooms);

        List<ChatRoom> actualRooms = chatRoomService.findRoomsForUser(testUser);

        assertThat(actualRooms).hasSize(2).containsExactlyInAnyOrder(room1, room2);
        verify(chatRoomRepository).findChatRoomsByUserId(testUser.getId());
    }

    @Test
    void findRoomsForUser_whenUserIsNull_shouldReturnEmptyList() {
        List<ChatRoom> actualRooms = chatRoomService.findRoomsForUser(null);
        assertThat(actualRooms).isEmpty();
        verify(chatRoomRepository, never()).findChatRoomsByUserId(anyLong());
    }

    // TODO: Add tests for addUserToRoom, removeUserFromRoom later
}