CREATE TABLE room_invitations (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    invited_user_id BIGINT NOT NULL,
    inviting_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_invitation_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_invited_user FOREIGN KEY (invited_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_inviting_user FOREIGN KEY (inviting_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_invitations_invited_user_status ON room_invitations(invited_user_id, status);
CREATE INDEX idx_invitations_room_invited_user ON room_invitations(room_id, invited_user_id);

CREATE UNIQUE INDEX uq_filtered_pending_invitation
ON room_invitations (room_id, invited_user_id)
WHERE status = 'PENDING';