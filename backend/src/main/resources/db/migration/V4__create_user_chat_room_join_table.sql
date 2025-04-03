CREATE TABLE user_chat_room (
    user_id BIGINT NOT NULL,
    chat_room_id BIGINT NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, chat_room_id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE
);