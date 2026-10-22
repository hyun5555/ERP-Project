CREATE TABLE IF NOT EXISTS user_number_sequence (
    number_year CHAR(4) PRIMARY KEY,
    last_number INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_number_sequence_range CHECK (last_number BETWEEN 0 AND 999)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS chat_room (
    room_no BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_a VARCHAR(50) NOT NULL,
    user_b VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at DATETIME NULL,
    UNIQUE KEY uk_chat_room_users (user_a, user_b),
    CONSTRAINT fk_chat_room_user_a FOREIGN KEY (user_a)
        REFERENCES user(usernum) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_room_user_b FOREIGN KEY (user_b)
        REFERENCES user(usernum) ON DELETE RESTRICT
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    message_no BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_no BIGINT NOT NULL,
    sender_usernum VARCHAR(50) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    CONSTRAINT fk_chat_message_room FOREIGN KEY (room_no)
        REFERENCES chat_room(room_no) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT,
    INDEX idx_chat_message_room_message (room_no, message_no),
    INDEX idx_chat_message_unread (room_no, sender_usernum, read_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
