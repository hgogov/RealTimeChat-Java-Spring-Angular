package com.chatapp.backend.repository;

import com.chatapp.backend.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomId(String roomId); // Find messages by chat room ID
    Page<ChatMessage> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);
}
