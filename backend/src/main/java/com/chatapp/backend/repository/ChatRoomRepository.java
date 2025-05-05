package com.chatapp.backend.repository;

import com.chatapp.backend.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByName(String name);

    @Query("SELECT cr FROM ChatRoom cr JOIN cr.members m WHERE m.id = :userId")
    List<ChatRoom> findChatRoomsByUserId(@Param("userId") Long userId);

    /**
     * Finds ChatRooms that are public (isPublic = true) and where the user with the given userId
     * is NOT a member.
     * Uses a subquery with NOT EXISTS for efficient filtering.
     *
     * @param userId The ID of the user looking for rooms to join.
     * @return A list of discoverable ChatRooms.
     */
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.isPublic = true AND NOT EXISTS " +
            "(SELECT 1 FROM cr.members m WHERE m.id = :userId)")
    List<ChatRoom> findDiscoverableRoomsForUser(@Param("userId") Long userId);
}