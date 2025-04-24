package com.chatapp.backend.service;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean isUserMemberOfRoom(String username, String roomName) {
        if (username == null || roomName == null) {
            return false;
        }
        Optional<ChatRoom> roomOpt = chatRoomRepository.findByName(roomName);
        if (roomOpt.isEmpty()) {
            return false;
        }
        return userRepository.existsByUsernameAndChatRooms_Id(username, roomOpt.get().getId());
    }

    @Transactional
    public ChatRoom createRoom(String roomName, User creator) {
        if (chatRoomRepository.findByName(roomName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name '" + roomName + "' already exists.");
        }

        ChatRoom newRoom = ChatRoom.builder()
                .name(roomName)
                .createdBy(creator)
                .build();

        newRoom.getMembers().add(creator);

        return chatRoomRepository.save(newRoom);
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> findRoomsForUser(User user) {
        return chatRoomRepository.findChatRoomsByUserId(user.getId());
    }

    // TODO: Add joinRoom, leaveRoom methods later
}