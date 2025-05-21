package com.chatapp.backend.service;

import com.chatapp.backend.config.DataInitializer;
import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.InvitationStatus;
import com.chatapp.backend.model.RoomInvitation;
import com.chatapp.backend.model.User;
import com.chatapp.backend.model.dto.CreateChatRoomRequest;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.RoomInvitationRepository;
import com.chatapp.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatRoomService {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private final RoomInvitationRepository roomInvitationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserRepository userRepository, RedisTemplate<String, String> redisTemplate, RoomInvitationRepository roomInvitationRepository, SimpMessagingTemplate messagingTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.roomInvitationRepository = roomInvitationRepository;
        this.messagingTemplate = messagingTemplate;
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

    @Transactional
    public void inviteUserToRoom(Long roomId, String usernameToInvite, User invitingUser) {
        log.info("User '{}' attempting to invite user '{}' to room ID: {}",
                invitingUser.getUsername(), usernameToInvite, roomId);

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!userRepository.existsByUsernameAndChatRooms_Id(invitingUser.getUsername(), roomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must be a member of the room to invite others.");
        }
        log.debug("Invite check: Inviting user '{}' confirmed member of room '{}'", invitingUser.getUsername(), room.getName());

        User userToInvite = userRepository.findByUsername(usernameToInvite)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User '" + usernameToInvite + "' not found."));

        if (invitingUser.getId().equals(userToInvite.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot invite yourself.");
        }

        User managedInvitingUser = userRepository.findById(invitingUser.getId()).orElseThrow();
        User managedUserToInvite = userRepository.findById(userToInvite.getId()).orElseThrow();
        ChatRoom managedRoom = chatRoomRepository.findById(roomId).orElseThrow();

        if (managedRoom.getMembers().contains(managedUserToInvite)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User '" + usernameToInvite + "' is already in the room.");
        }

        if (roomInvitationRepository.existsByRoomAndInvitedUserAndStatus(managedRoom, managedUserToInvite, InvitationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User '" + usernameToInvite + "' already has a pending invitation to this room.");
        }

        RoomInvitation invitation = RoomInvitation.builder()
                .room(managedRoom)
                .invitedUser(managedUserToInvite)
                .invitingUser(managedInvitingUser)
                .status(InvitationStatus.PENDING)
                .build();
        roomInvitationRepository.save(invitation);

        log.info("Invitation created for user '{}' to join room '{}' by user '{}'. ID: {}",
                usernameToInvite, room.getName(), invitingUser.getUsername(), invitation.getId());

        Map<String, Object> notificationPayload = Map.of(
                "type", "NEW_INVITATION",
                "invitationId", invitation.getId(),
                "roomId", room.getId(),
                "roomName", room.getName(),
                "inviterUsername", invitingUser.getUsername()
        );

        messagingTemplate.convertAndSendToUser(
                managedUserToInvite.getUsername(),
                "/queue/invitations",
                notificationPayload
        );
        log.info("Sent NEW_INVITATION notification to user '{}' for room '{}'", managedUserToInvite.getUsername(), room.getName());
    }


    @Transactional
    public void acceptRoomInvitationAndAddUser(Long invitationId, User userAccepting) {
        log.info("User '{}' attempting to accept invitation ID: {}", userAccepting.getUsername(), invitationId);

        RoomInvitation invitation = roomInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is no longer pending (status: " + invitation.getStatus() + ").");
        }

        ChatRoom room = invitation.getRoom();
        User managedUserAccepting = userRepository.findById(userAccepting.getId()).orElseThrow();

        if (!room.getMembers().contains(managedUserAccepting)) {
            managedUserAccepting.getChatRooms().add(room);
            userRepository.save(managedUserAccepting);
            log.info("User '{}' added to room '{}' members after accepting invitation.", managedUserAccepting.getUsername(), room.getName());
        } else {
            log.warn("User '{}' was already a member of room '{}' upon accepting invite.", managedUserAccepting.getUsername(), room.getName());
        }


        invitation.setStatus(InvitationStatus.ACCEPTED);
        roomInvitationRepository.save(invitation);
        log.info("Invitation ID {} status updated to ACCEPTED for user '{}'", invitationId, userAccepting.getUsername());

        User invitingUser = invitation.getInvitingUser();
        if (invitingUser != null) {
            Map<String, Object> acceptNotification = Map.of(
                    "type", "INVITATION_ACCEPTED",
                    "roomId", room.getId(),
                    "roomName", room.getName(),
                    "acceptedByUsername", userAccepting.getUsername()
            );
            messagingTemplate.convertAndSendToUser(
                    invitingUser.getUsername(),
                    "/queue/notifications",
                    acceptNotification
            );
            log.info("Sent INVITATION_ACCEPTED notification to inviter '{}'", invitingUser.getUsername());
        }
    }

    @Transactional
    public void declineRoomInvitation(Long invitationId, User userDeclining) {
        log.info("User '{}' attempting to decline invitation ID: {}", userDeclining.getUsername(), invitationId);
        RoomInvitation invitation = roomInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is no longer pending.");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        roomInvitationRepository.save(invitation);
        log.info("Invitation ID {} status updated to DECLINED for user '{}'", invitationId, userDeclining.getUsername());
    }
}