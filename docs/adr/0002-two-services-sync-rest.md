# 0002 兩個服務、同步 REST、單一 MySQL 兩個 schema

- 狀態：採用
- 日期：2026-10-31（整理自 research.md 決策 5、8、11）

## 背景

需求包含 LINE Bot（Webhook、每月推播）與家庭支出管理。LINE Bot 有獨立的對外入口、排程與失敗重試；支出管理是主要業務。部署目標是 Northflank 免費方案（2 個 service、1 個 MySQL）。

## 決定

- 切成兩個部署單元：**app-service**（family／expense／statistics 模組）與 **notification-service**（Webhook、排程、推播紀錄）。
- 唯一的跨進程呼叫是 notification-service → app-service 的同步 REST，以共用密鑰 `X-Internal-Token` 驗證（常數時間比較）。
- app-service 內部各模組之間用 Java 方法呼叫；統計不另建資料表，每次由 expense 模組即時彙總。
- 單一 MySQL 執行個體，兩個 schema（`appdb`、`notificationdb`），各服務使用自己專用的資料庫帳號。

## 影響

- 好處：Bot 的失敗與重試不影響主要業務；服務邊界與資料所有權清楚；不需要訊息佇列或服務發現。
- 代價：notification-service 依賴 app-service 可用性。排程對每位成員獨立 try/catch 並記錄 FAILED，單一成員或單一家庭失敗不會中斷整批，但 app-service 整體停擺時當月通知仍會失敗。
- 取捨：家庭規模下不需要非同步訊息；若日後通知量大，再評估改用佇列。
