-- Existing databases only. Back up DB and attachments and stop application writes first.
-- MySQL DDL commits implicitly; each ALTER is atomic, the complete file is not one transaction.
-- This changes only user-reference delete rules. It does not delete or rewrite business rows.
-- MySQL 8.4 with FK checks enabled cannot combine DROP and ADD FOREIGN KEY in one ALTER.
-- Keep a separately named RESTRICT guard while replacing each canonical constraint.
-- Re-running the completed file is supported with the names from ERP.sql and schema.sql.
-- After interruption, keep writes stopped, inspect SHOW CREATE TABLE, and resume the
-- unfinished table at its next step. Do not remove a guard until its canonical FK exists.
-- https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html
USE erp;

ALTER TABLE notice
    ADD CONSTRAINT fk_notice_user_preserve_guard FOREIGN KEY (usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE notice DROP FOREIGN KEY fk_notice_user;
ALTER TABLE notice
    ADD CONSTRAINT fk_notice_user FOREIGN KEY (usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE notice DROP FOREIGN KEY fk_notice_user_preserve_guard;

ALTER TABLE approval
    ADD CONSTRAINT fk_approval_user_preserve_guard FOREIGN KEY (usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE approval DROP FOREIGN KEY fk_approval_user;
ALTER TABLE approval
    ADD CONSTRAINT fk_approval_user FOREIGN KEY (usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE approval DROP FOREIGN KEY fk_approval_user_preserve_guard;

ALTER TABLE approval_line
    ADD CONSTRAINT fk_approval_line_user_preserve_guard FOREIGN KEY (approval_target)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE approval_line DROP FOREIGN KEY fk_approval_line_user;
ALTER TABLE approval_line
    ADD CONSTRAINT fk_approval_line_user FOREIGN KEY (approval_target)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE approval_line DROP FOREIGN KEY fk_approval_line_user_preserve_guard;

ALTER TABLE chat_room
    ADD CONSTRAINT fk_chat_room_user_a_preserve_guard FOREIGN KEY (user_a)
        REFERENCES user(usernum) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_chat_room_user_b_preserve_guard FOREIGN KEY (user_b)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE chat_room
    DROP FOREIGN KEY fk_chat_room_user_a,
    DROP FOREIGN KEY fk_chat_room_user_b;
ALTER TABLE chat_room
    ADD CONSTRAINT fk_chat_room_user_a FOREIGN KEY (user_a)
        REFERENCES user(usernum) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_chat_room_user_b FOREIGN KEY (user_b)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE chat_room
    DROP FOREIGN KEY fk_chat_room_user_a_preserve_guard,
    DROP FOREIGN KEY fk_chat_room_user_b_preserve_guard;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_sender_preserve_guard FOREIGN KEY (sender_usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE chat_message DROP FOREIGN KEY fk_chat_message_sender;
ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_usernum)
        REFERENCES user(usernum) ON DELETE RESTRICT;
ALTER TABLE chat_message DROP FOREIGN KEY fk_chat_message_sender_preserve_guard;
