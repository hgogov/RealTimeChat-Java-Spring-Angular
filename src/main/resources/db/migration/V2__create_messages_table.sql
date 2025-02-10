CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    sender VARCHAR(50) NOT NULL,
    room_id VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);