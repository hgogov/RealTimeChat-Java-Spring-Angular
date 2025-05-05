package com.chatapp.backend.service;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
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
    private User anotherUser;
    private ChatRoom testRoom;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setChatRooms(new HashSet<>());

        anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setUsername("anotheruser");
        anotherUser.setChatRooms(new HashSet<>());


        testRoom = ChatRoom.builder()
                .id(10L)
                .name("Test Room")
                .createdBy(testUser)
                .members(new HashSet<>())
                .build();
        testRoom.getMembers().add(testUser);
        testUser.getChatRooms().add(testRoom);
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
        String newRoomName = "New Room";
        boolean isPublic = true;

        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName(newRoomName);
        request.setPublic(isPublic);

        when(chatRoomRepository.findByName(newRoomName)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom roomArg = invocation.getArgument(0);
            roomArg.setId(11L);
            return roomArg;
        });
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        ChatRoom createdRoom = chatRoomService.createRoom(request, testUser);

        assertNotNull(createdRoom);
        assertEquals(newRoomName, createdRoom.getName());
        assertEquals(isPublic, createdRoom.isPublic());
        assertEquals(testUser, createdRoom.getCreatedBy());
        assertNotNull(createdRoom.getId());
        assertTrue(createdRoom.getMembers().contains(testUser));

        verify(chatRoomRepository).findByName(newRoomName);

        ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getName()).isEqualTo(newRoomName);
        assertThat(roomCaptor.getValue().isPublic()).isEqualTo(isPublic);

        verify(userRepository).save(testUser);
    }

    @Test
    void createRoom_whenNameExists_shouldThrowException() {
        String existingRoomName = "Existing Room";
        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName(existingRoomName);
        request.setPublic(true);

        when(chatRoomRepository.findByName(existingRoomName)).thenReturn(Optional.of(testRoom));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.createRoom(request, testUser);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("already exists"));

        verify(chatRoomRepository).findByName(existingRoomName);
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

    // Tests for joinRoom
    @Test
    void joinRoom_whenRoomAndUserExistAndNotMember_shouldAddUserToRoom() {
        Long roomId = testRoom.getId();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(anotherUser.getId())).thenReturn(Optional.of(anotherUser));

        assertThat(testRoom.getMembers()).doesNotContain(anotherUser);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatRoomService.joinRoom(roomId, anotherUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getChatRooms()).contains(testRoom);

        verify(chatRoomRepository).findById(roomId);
        verify(userRepository).findById(anotherUser.getId());
    }

    @Test
    void joinRoom_whenRoomNotFound_shouldThrowNotFoundException() {
        Long nonExistentRoomId = 99L;
        when(chatRoomRepository.findById(nonExistentRoomId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.joinRoom(nonExistentRoomId, testUser);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Room not found"));
        verify(userRepository, never()).findById(anyLong());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void joinRoom_whenUserNotFound_shouldThrowInternalServerError() {
        Long roomId = testRoom.getId();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.joinRoom(roomId, testUser);
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(exception.getReason().contains("User record not found"));
        verify(userRepository, never()).save(any(User.class));
    }


    @Test
    void joinRoom_whenUserAlreadyMember_shouldThrowBadRequestException() {
        Long roomId = testRoom.getId();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        assertTrue(testRoom.getMembers().contains(testUser));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.joinRoom(roomId, testUser);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("already in room"));
        verify(userRepository, never()).save(any(User.class));
    }


    // Tests for leaveRoom

    @Test
    void leaveRoom_whenRoomAndUserExistAndIsMember_shouldRemoveUserFromRoom() {
        Long roomId = testRoom.getId();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        assertTrue(testRoom.getMembers().contains(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatRoomService.leaveRoom(roomId, testUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getChatRooms()).doesNotContain(testRoom);

        verify(chatRoomRepository).findById(roomId);
        verify(userRepository).findById(testUser.getId());
    }

    @Test
    void leaveRoom_whenRoomNotFound_shouldThrowNotFoundException() {
        Long nonExistentRoomId = 99L;
        when(chatRoomRepository.findById(nonExistentRoomId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.leaveRoom(nonExistentRoomId, testUser);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Room not found"));
        verify(userRepository, never()).findById(anyLong());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void leaveRoom_whenUserNotFound_shouldThrowInternalServerError() {
        Long roomId = testRoom.getId();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.leaveRoom(roomId, testUser);
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(exception.getReason().contains("User record not found"));
        verify(userRepository, never()).save(any(User.class));
    }


    @Test
    void leaveRoom_whenUserNotMember_shouldThrowBadRequestException() {
        Long roomId = testRoom.getId();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(anotherUser.getId())).thenReturn(Optional.of(anotherUser));
        assertFalse(testRoom.getMembers().contains(anotherUser));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            chatRoomService.leaveRoom(roomId, anotherUser);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("User not in room"));
        verify(userRepository, never()).save(any(User.class));
    }

    // Tests for findDiscoverableRooms
    @Test
    void findDiscoverableRooms_whenUserIsValid_shouldCallRepositoryAndReturnList() {
        ChatRoom discoverableRoom1 = ChatRoom.builder().id(20L).name("Discoverable 1").isPublic(true).build();
        ChatRoom discoverableRoom2 = ChatRoom.builder().id(21L).name("Discoverable 2").isPublic(true).build();
        List<ChatRoom> mockRepoResponse = List.of(discoverableRoom1, discoverableRoom2);

        when(chatRoomRepository.findDiscoverableRoomsForUser(testUser.getId())).thenReturn(mockRepoResponse);

        List<ChatRoom> actualRooms = chatRoomService.findDiscoverableRooms(testUser);

        assertThat(actualRooms).isEqualTo(mockRepoResponse);
        assertThat(actualRooms).hasSize(2);
        verify(chatRoomRepository, times(1)).findDiscoverableRoomsForUser(testUser.getId());
    }

    @Test
    void findDiscoverableRooms_whenUserIsNull_shouldReturnEmptyList() {
        List<ChatRoom> actualRooms = chatRoomService.findDiscoverableRooms(null);

        assertThat(actualRooms).isNotNull().isEmpty();
        verify(chatRoomRepository, never()).findDiscoverableRoomsForUser(anyLong());
    }

    @Test
    void findDiscoverableRooms_whenUserHasNullId_shouldReturnEmptyList() {
        User userWithNullId = new User();
        userWithNullId.setUsername("noIdUser");

        List<ChatRoom> actualRooms = chatRoomService.findDiscoverableRooms(userWithNullId);

        assertThat(actualRooms).isNotNull().isEmpty();
        verify(chatRoomRepository, never()).findDiscoverableRoomsForUser(anyLong());
    }
}