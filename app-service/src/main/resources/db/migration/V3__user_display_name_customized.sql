-- 使用者（或群組管理者）自訂過顯示名稱後，LINE 登入不再以 LINE 暱稱覆蓋
ALTER TABLE users
    ADD COLUMN display_name_customized BOOLEAN NOT NULL DEFAULT FALSE AFTER display_name;
