ALTER TABLE chat_rooms
ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_chat_rooms_is_public ON chat_rooms(is_public);
