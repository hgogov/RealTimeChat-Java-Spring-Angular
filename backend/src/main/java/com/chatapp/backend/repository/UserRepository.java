package com.chatapp.backend.repository;

import com.chatapp.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsernameAndChatRooms_Id(String username, Long chatRoomId);
}
