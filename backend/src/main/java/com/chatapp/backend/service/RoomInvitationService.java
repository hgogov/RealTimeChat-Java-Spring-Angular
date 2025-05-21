package com.chatapp.backend.service;

import com.chatapp.backend.model.InvitationStatus;
import com.chatapp.backend.model.RoomInvitation;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.RoomInvitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomInvitationService {

    private static final Logger log = LoggerFactory.getLogger(RoomInvitationService.class);

    private final RoomInvitationRepository roomInvitationRepository;

    public RoomInvitationService(RoomInvitationRepository roomInvitationRepository) {
        this.roomInvitationRepository = roomInvitationRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomInvitation> getPendingInvitationsForUser(User user) {
        log.debug("Fetching pending invitations for user: {}", user.getUsername());

        return roomInvitationRepository.findDetailedPendingInvitationsForUser(user, InvitationStatus.PENDING);
    }
}