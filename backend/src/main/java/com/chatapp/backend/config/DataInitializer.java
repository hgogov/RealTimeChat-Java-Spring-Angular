package com.chatapp.backend.config;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    public static final String GENERAL_ROOM_NAME = "General";

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatRoomService chatRoomService;

    public DataInitializer(ChatRoomRepository chatRoomRepository,
                           UserRepository userRepository,
                           ChatRoomService chatRoomService) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.chatRoomService = chatRoomService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Checking for default 'General' chat room...");

        Optional<ChatRoom> generalRoomOpt = chatRoomRepository.findByName(GENERAL_ROOM_NAME);

        if (generalRoomOpt.isEmpty()) {
            log.info("'{}' room not found, creating it...", GENERAL_ROOM_NAME);
            try {

                ChatRoom generalRoom = ChatRoom.builder()
                        .name(GENERAL_ROOM_NAME)
                        .createdBy(null)
                        .build();
                chatRoomRepository.save(generalRoom);
                log.info("Default '{}' room created successfully.", GENERAL_ROOM_NAME);


            } catch (Exception e) {
                log.error("Failed to create default '{}' room: {}", GENERAL_ROOM_NAME, e.getMessage(), e);
            }
        } else {
            log.info("Default '{}' room already exists.", GENERAL_ROOM_NAME);
        }
    }

    private User getPlaceholderCreator() {
        User placeholder = new User();
        placeholder.setUsername("system");
        placeholder.setEmail("system@chat.app");
        placeholder.setPassword("----");
        return placeholder;
    }
}