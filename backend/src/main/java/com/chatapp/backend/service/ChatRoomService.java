package com.chatapp.backend.service;

import com.chatapp.backend.config.DataInitializer;
import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatRoomService {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserRepository userRepository, RedisTemplate<String, String> redisTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public boolean isUserMemberOfRoom(String username, String roomName) {
        if (username == null || roomName == null) {
            return false;
        }
        Optional<ChatRoom> roomOpt = chatRoomRepository.findByName(roomName);
        if (roomOpt.isEmpty()) {
            log.warn("Membership check failed: Room '{}' not found.", roomName);
            return false;
        }
        boolean isMember = userRepository.existsByUsernameAndChatRooms_Id(username, roomOpt.get().getId());
        log.debug("Membership check: User '{}' in room '{}' (ID: {}) -> {}", username, roomName, roomOpt.get().getId(), isMember);
        return isMember;
    }

    @Transactional
    public ChatRoom createRoom(CreateChatRoomRequest request, User creator) {
        String roomName = request.getName().trim();
        boolean isPublic = (request.getIsPublic() == null) ? true : request.getIsPublic();

        log.info("Attempting to create room '{}' (isPublic: {}) by user '{}'", roomName, isPublic, creator.getUsername());
        chatRoomRepository.findByName(roomName).ifPresent(existingRoom -> {
            log.warn("Room creation failed: Name '{}' already exists (ID: {}).", roomName, existingRoom.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name '" + roomName + "' already exists.");
        });

        ChatRoom newRoom = ChatRoom.builder()
                .name(roomName)
                .createdBy(creator)
                .isPublic(isPublic)
                .build();

        newRoom.getMembers().add(creator);
        creator.getChatRooms().add(newRoom);

        ChatRoom savedRoom = chatRoomRepository.save(newRoom);
        userRepository.save(creator);

        log.info("Successfully created room '{}' (ID: {}, isPublic: {}) for user '{}'",
                savedRoom.getName(), savedRoom.getId(), savedRoom.isPublic(), creator.getUsername());
        return savedRoom;
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> findRoomsForUser(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Cannot fetch rooms for null user or user without ID.");
            return List.of();
        }
        log.debug("Fetching rooms for user '{}' (ID: {})", user.getUsername(), user.getId());

        List<ChatRoom> rooms = chatRoomRepository.findChatRoomsByUserId(user.getId());
        log.debug("Found {} rooms for user '{}'", rooms.size(), user.getUsername());
        return rooms;
    }

    @Transactional
    public void addUserToRoom(Long roomId, User userToAdd) {
        log.info("Attempting to add user '{}' (ID: {}) to room ID {}", userToAdd.getUsername(), userToAdd.getId(), roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> {
                    log.warn("Add user failed: Room ID {} not found.", roomId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                });
        User managedUser = userRepository.findById(userToAdd.getId())
                .orElseThrow(() -> {
                    log.warn("Add user failed: User ID {} not found.", userToAdd.getId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });

        if (room.getMembers().contains(managedUser)) {
            log.warn("User '{}' is already a member of room '{}'. No add action needed.", managedUser.getUsername(), room.getName());
            return;
        }

        managedUser.getChatRooms().add(room);
        userRepository.save(managedUser);

        log.info("Successfully added user '{}' to room '{}' (ID: {})", managedUser.getUsername(), room.getName(), room.getId());
    }

    @Transactional
    public void removeUserFromRoom(Long roomId, User userToRemove) {
        log.info("Attempting to remove user '{}' from room ID {}", userToRemove.getUsername(), roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> {
                    log.warn("Leave room failed: Room ID {} not found.", roomId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                });
        User user = userRepository.findById(userToRemove.getId())
                .orElseThrow(() -> {
                    log.warn("Leave room failed: User ID {} not found.", userToRemove.getId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });


        if (!room.getMembers().contains(user)) {
            log.warn("User '{}' is not a member of room '{}'. Cannot remove.", user.getUsername(), room.getName());
            return;
        }

        room.getMembers().remove(user);
        chatRoomRepository.save(room);
        log.info("Successfully removed user '{}' from room '{}'", user.getUsername(), room.getName());
    }

    @Transactional
    public void joinRoom(Long roomId, User user) {
        log.info("User '{}' attempting to join room ID: {}", user.getUsername(), roomId);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> {
                    log.warn("Join room failed for user '{}': Room ID {} not found.", user.getUsername(), roomId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                });

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> {
                    log.error("Join room failed: Authenticated user ID {} not found in DB.", user.getId());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User record not found");
                });

        if (!room.isPublic()) {
            log.warn("User '{}' denied joining private room '{}' (ID: {})", user.getUsername(), room.getName(), roomId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot join private room '" + room.getName() + "' without an invitation.");
        }

        if (room.getMembers().contains(managedUser)) {
            log.warn("User '{}' is already a member of room '{}'. No action taken.", managedUser.getUsername(), room.getName());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already in room '" + room.getName() + "'");
        }

        managedUser.getChatRooms().add(room);
        userRepository.save(managedUser);

        log.info("User '{}' successfully joined room '{}' (ID: {})", managedUser.getUsername(), room.getName(), room.getId());
    }

    @Transactional
    public void leaveRoom(Long roomId, User user) {
        log.info("User '{}' attempting to leave room ID: {}", user.getUsername(), roomId);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> {
                    log.warn("Leave room failed for user '{}': Room ID {} not found.", user.getUsername(), roomId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                });

        if (DataInitializer.GENERAL_ROOM_NAME.equalsIgnoreCase(room.getName())) {
            log.warn("User '{}' attempted to leave the default '{}' room. Denied.", user.getUsername(), DataInitializer.GENERAL_ROOM_NAME);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot leave the default 'General' room.");
        }

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> {
                    log.error("Leave room failed: Authenticated user ID {} not found in DB.", user.getId());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User record not found");
                });

        if (!room.getMembers().contains(managedUser)) {
            log.warn("User '{}' is not a member of room '{}'. Cannot leave.", managedUser.getUsername(), room.getName());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not in room '" + room.getName() + "'");
        }

        managedUser.getChatRooms().remove(room);
        userRepository.save(managedUser);

        log.info("User '{}' successfully left room '{}' (ID: {})", managedUser.getUsername(), room.getName(), room.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> findDiscoverableRooms(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Cannot find discoverable rooms for null user or user without ID.");
            return List.of();
        }
        log.debug("Finding discoverable rooms for user '{}' (ID: {})", user.getUsername(), user.getId());
        List<ChatRoom> rooms = chatRoomRepository.findDiscoverableRoomsForUser(user.getId());
        log.debug("Found {} discoverable rooms for user '{}'", rooms.size(), user.getUsername());
        return rooms;
    }

    @Transactional(readOnly = true)
    public List<String> getOnlineMembers(Long roomId) {
        log.debug("Fetching online members for room ID: {}", roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Set<User> members = room.getMembers();
        if (members.isEmpty()) {
            return List.of();
        }

        // Check Redis for online status of each member
        List<String> onlineUsernames = members.stream()
                .map(User::getUsername)
                .filter(username -> Boolean.TRUE.equals(redisTemplate.hasKey("user:" + username + ":online")))
                .toList();

        log.debug("Found online members for room ID {}: {}", roomId, onlineUsernames);
        return onlineUsernames;
    }
}