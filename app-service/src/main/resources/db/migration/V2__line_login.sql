-- 改以 LINE 身分登入：移除 Email/密碼與綁定碼流程（既有 dev 資料直接捨棄）
DROP TABLE line_binding_codes;
DROP TABLE line_bindings;

DELETE FROM expense_records;
DELETE FROM payment_accounts;
DELETE FROM family_members;
DELETE FROM family_groups;
DELETE FROM users;

ALTER TABLE users
    DROP INDEX uk_users_email,
    DROP COLUMN email,
    DROP COLUMN password_hash,
    ADD COLUMN line_user_id VARCHAR(64) NOT NULL AFTER id,
    ADD COLUMN display_name VARCHAR(255) NOT NULL AFTER line_user_id,
    ADD CONSTRAINT uk_users_line_user_id UNIQUE (line_user_id);
