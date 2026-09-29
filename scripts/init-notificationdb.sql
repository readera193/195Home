-- 本機開發用：MySQL 官方 image 只會用 MYSQL_DATABASE/MYSQL_USER 建立 appdb 與 app 使用者，
-- 這裡額外建立 notification-service 專用的 notificationdb schema 與 notification 使用者，
-- 對應 constitution v3.0.0：單一 MySQL 執行個體、appdb/notificationdb 兩個 schema。
CREATE DATABASE IF NOT EXISTS notificationdb CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'notification'@'%' IDENTIFIED BY 'notification';
GRANT ALL PRIVILEGES ON notificationdb.* TO 'notification'@'%';
FLUSH PRIVILEGES;
