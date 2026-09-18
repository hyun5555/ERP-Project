SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE erp;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_room;
DROP TABLE IF EXISTS approval_line;
DROP TABLE IF EXISTS approval_file;
DROP TABLE IF EXISTS approval;
DROP TABLE IF EXISTS notice_team;
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS user;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE user (
    usernum VARCHAR(50) PRIMARY KEY,
    userpw VARCHAR(255) NOT NULL,
    name VARCHAR(20) NOT NULL,
    idnum1 VARCHAR(50),
    idnum2 VARCHAR(1),
    phonenum VARCHAR(20),
    officenum VARCHAR(20),
    email VARCHAR(100),
    team VARCHAR(50),
    level VARCHAR(50),
    firstlogin BOOLEAN NOT NULL DEFAULT TRUE,
    joindate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    authority BOOLEAN NOT NULL DEFAULT FALSE,
    level_num INT,
    user_status VARCHAR(50) NOT NULL DEFAULT '재직'
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE notice (
    notice_no INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    notice_title VARCHAR(255) NOT NULL,
    notice_content TEXT NOT NULL,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    pname VARCHAR(255),
    fname VARCHAR(255),
    noticedate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usernum VARCHAR(50),
    CONSTRAINT fk_notice_user FOREIGN KEY (usernum) REFERENCES user(usernum)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE notice_team (
    notice_no INT NOT NULL,
    notice_team VARCHAR(50) NOT NULL,
    CONSTRAINT fk_notice_team_notice FOREIGN KEY (notice_no)
        REFERENCES notice(notice_no) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE approval (
    approval_no INT PRIMARY KEY AUTO_INCREMENT,
    kind VARCHAR(50),
    writedate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_title VARCHAR(255),
    approval_content TEXT,
    document_status VARCHAR(255),
    usernum VARCHAR(50),
    approval_code VARCHAR(255),
    CONSTRAINT fk_approval_user FOREIGN KEY (usernum)
        REFERENCES user(usernum) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE approval_file (
    approval_no INT NOT NULL,
    apname VARCHAR(255) NOT NULL,
    afname VARCHAR(255),
    PRIMARY KEY (approval_no, apname),
    CONSTRAINT fk_approval_file_approval FOREIGN KEY (approval_no)
        REFERENCES approval(approval_no) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE approval_line (
    approval_no INT NOT NULL,
    approval_target VARCHAR(50) NOT NULL,
    approval_status VARCHAR(255),
    approval_sort INT,
    approval_date DATETIME,
    comment VARCHAR(255),
    PRIMARY KEY (approval_no, approval_target),
    CONSTRAINT fk_approval_line_user FOREIGN KEY (approval_target)
        REFERENCES user(usernum) ON DELETE CASCADE,
    CONSTRAINT fk_approval_line_approval FOREIGN KEY (approval_no)
        REFERENCES approval(approval_no) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE chat_room (
    room_no BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_a VARCHAR(50) NOT NULL,
    user_b VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at DATETIME NULL,
    UNIQUE KEY uk_chat_room_users (user_a, user_b),
    CONSTRAINT fk_chat_room_user_a FOREIGN KEY (user_a)
        REFERENCES user(usernum) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room_user_b FOREIGN KEY (user_b)
        REFERENCES user(usernum) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    message_no BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_no BIGINT NOT NULL,
    sender_usernum VARCHAR(50) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    CONSTRAINT fk_chat_message_room FOREIGN KEY (room_no)
        REFERENCES chat_room(room_no) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_usernum)
        REFERENCES user(usernum) ON DELETE CASCADE,
    INDEX idx_chat_message_room_message (room_no, message_no),
    INDEX idx_chat_message_unread (room_no, sender_usernum, read_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO user
    (usernum, userpw, name, idnum1, idnum2, phonenum, officenum, email,
     team, level, firstlogin, authority, level_num, user_status)
VALUES
	-- Legacy MD5 fixtures are upgraded to BCrypt after the first successful login.
    ('admin', MD5('1234'), '관리자', '123456', '1', '010-0000-0000', '02-0000-0000',
     'admin@example.com', '경영지원', '관리자', FALSE, TRUE, 0, '재직'),
    ('2005001', MD5('1234'), '김사장', NULL, NULL, NULL, NULL, NULL,
     '임원', '사장', FALSE, FALSE, 100, '재직'),
    ('2005002', MD5('1234'), '김팀장', NULL, NULL, NULL, NULL, NULL,
     '개발', '팀장', FALSE, FALSE, 200, '재직'),
    ('2005003', MD5('1234'), '김대리', NULL, NULL, NULL, NULL, NULL,
     '개발', '대리', FALSE, FALSE, 300, '재직'),
    ('2005004', MD5('1234'), '김사원', NULL, NULL, NULL, NULL, NULL,
     '개발', '사원', FALSE, FALSE, 400, '재직'),
    ('2005005', MD5('1234'), '박팀장', NULL, NULL, NULL, NULL, NULL,
     '디자인', '팀장', FALSE, FALSE, 200, '재직'),
    ('2005006', MD5('1234'), '박대리', NULL, NULL, NULL, NULL, NULL,
     '디자인', '대리', FALSE, FALSE, 300, '재직'),
    ('2005007', MD5('1234'), '박사원', NULL, NULL, NULL, NULL, NULL,
     '디자인', '사원', FALSE, FALSE, 400, '재직'),
    ('2005008', MD5('1234'), '오팀장', NULL, NULL, NULL, NULL, NULL,
     '경영지원', '팀장', FALSE, FALSE, 200, '재직'),
    ('2005009', MD5('1234'), '오대리', NULL, NULL, NULL, NULL, NULL,
     '경영지원', '대리', FALSE, FALSE, 300, '재직'),
    ('2005010', MD5('1234'), '오사원', NULL, NULL, NULL, NULL, NULL,
     '경영지원', '사원', FALSE, FALSE, 400, '재직');

INSERT INTO notice
    (notice_title, notice_content, is_important, is_main, usernum)
VALUES
    ('ERP 개발 환경 안내', 'Spring Boot 전환 개발 환경이 준비되었습니다.', TRUE, TRUE, 'admin');

INSERT INTO notice_team (notice_no, notice_team)
VALUES (LAST_INSERT_ID(), '999');

INSERT INTO approval
    (kind, approval_title, approval_content, document_status, usernum, approval_code)
VALUES
    ('기안서', 'Spring Boot 전환 검토', '기존 ERP 시스템의 Spring Boot 전환을 검토합니다.',
     '대기중', '2005004', '20260917-001');

INSERT INTO approval_line
    (approval_no, approval_target, approval_status, approval_sort)
VALUES
    (LAST_INSERT_ID(), '2005002', '대기', 1);
