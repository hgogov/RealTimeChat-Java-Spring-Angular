package com.chatapp.backend.repository;

import com.chatapp.backend.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByRoomId(String roomId); // Find messages by chat room ID
}
