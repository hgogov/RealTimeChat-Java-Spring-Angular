package com.chatapp.backend.repository;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.InvitationStatus;
import com.chatapp.backend.model.RoomInvitation;
import com.chatapp.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {

    List<RoomInvitation> findByInvitedUserAndStatus(User invitedUser, InvitationStatus status);

    Optional<RoomInvitation> findByRoomAndInvitedUserAndStatus(ChatRoom room, User invitedUser, InvitationStatus status);

    boolean existsByRoomAndInvitedUserAndStatus(ChatRoom room, User invitedUser, InvitationStatus status);

    @Query("SELECT ri FROM RoomInvitation ri " +
            "JOIN FETCH ri.room r " +
            "JOIN FETCH ri.invitingUser iu " +
            "WHERE ri.invitedUser = :invitedUser AND ri.status = :status " +
            "ORDER BY ri.createdAt DESC")
    List<RoomInvitation> findDetailedPendingInvitationsForUser(@Param("invitedUser") User invitedUser, @Param("status") InvitationStatus status);
}