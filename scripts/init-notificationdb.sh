#!/bin/bash
# 本機開發用：MySQL 官方 image 只會用 MYSQL_DATABASE/MYSQL_USER 建立 appdb 與 app 使用者，
# 這裡額外建立 notification-service 專用的 notificationdb schema 與 notification 使用者，
# 對應 constitution v3.0.0：單一 MySQL 執行個體、appdb/notificationdb 兩個 schema。
# 兩個使用者各自只擁有自己 schema 的權限；密碼來自 docker-compose 傳入的環境變數。
# 注意：init 腳本只在資料目錄第一次建立時執行，之後改密碼需重建 volume（docker compose down -v）。
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS notificationdb CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'notification'@'%' IDENTIFIED BY '${NOTIFICATION_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON notificationdb.* TO 'notification'@'%';
FLUSH PRIVILEGES;
SQL
