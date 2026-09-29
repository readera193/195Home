# Phase 0 Research: 家庭共享支出平台

本文件彙整 Technical Context 中需要決策的技術項目，僅記錄最新決策結果（依 constitution 之文件記錄規範，不保留討論過程）。

## 1. 部署目標平台（CD 實際部署方式）

- **Decision**: GitHub Actions 於 CI 完成 build + test 後，CD pipeline 建置各服務 Docker 映像檔並推送至 GitHub Container Registry (GHCR)；再透過 SSH 連線到一台已安裝 Docker / Docker Compose 的可存取 VM（雲端或自有主機皆可），執行 `docker compose pull && docker compose up -d` 完成部署。
- **Rationale**: 直接沿用本機一致的 `docker-compose.yml` 設定，學習成本最低、能快速驗證整套系統可運作，且不綁定特定雲端 PaaS 廠商的專屬部署流程，符合原則 V（CI/CD 需含實際部署）與原則 VII（容器化與一鍵啟動）的精神一致性。

## 2. LINE Messaging API 整合方式

- **Decision**: notification-service 使用 LINE 官方 `line-bot-sdk-java` 函式庫處理 Webhook 簽章驗證、訊息解析與推播發送。
- **Rationale**: 官方 SDK 已處理簽章驗證與訊息序列化細節，降低手刻整合時的安全風險（例如簽章驗證邏輯出錯導致偽造訊息被接受）。

## 3. 併發編輯鎖定機制（FR-027）

- **Decision**: 於 `expense_records` 資料表新增 `locked_by_member_id`、`locked_at` 欄位。取得編輯權時以單一交易執行條件式 UPDATE：`WHERE id = ? AND (locked_by_member_id IS NULL OR locked_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 MINUTE))`，影響列數為 1 才視為取得鎖；儲存或取消編輯時清除鎖定欄位；鎖定 TTL 設為 5 分鐘，避免使用者異常關閉頁面導致永久鎖死。
- **Rationale**: 善用既有 MySQL 交易機制即可達成互斥控制，不需引入 Redis 等分散式鎖工具，符合技術邊界限制（不得新增資料庫技術）與原則 VI（可讀性優先）。

## 4. 統計資料計算策略（FR-011）

- **Decision**: statistics-service 不建立獨立資料庫，每次請求即時呼叫 expense-service 的 REST API 取得指定月份、指定家庭的支出紀錄，於記憶體中依支付帳戶彙總淨額後回傳，不做持久化快取。
- **Rationale**: 家庭規模資料量小（單一家庭每月約數十至數百筆紀錄），即時運算即可滿足 SC-003（篩選 2 秒內顯示）與統計頁效能需求，同時避免快取層與資料同步一致性問題，符合原則 VI 簡潔優先。

## 5. Spring Cloud Config Backend

- **Decision**: 採用 native file-based backend，設定檔集中放置於專案內 `config-repo/` 目錄，由 config-service 啟動時讀取並提供給其他服務。
- **Rationale**: 展示 Spring Cloud Config 集中式設定管理的核心用法，同時避免額外維護獨立 Git repository 的複雜度。

## 6. 服務間通訊與服務發現

- **Decision**: 不引入服務註冊中心（如 Eureka），服務間呼叫（gateway→各服務、statistics→expense、notification→family/statistics）採用 Spring Cloud Gateway 靜態路由設定，並透過 docker-compose 網路的服務名稱（如 `http://expense-service:8080`）直接呼叫。
- **Rationale**: docker-compose 環境下服務名稱即為穩定的 DNS 位址，家庭規模應用的服務拓樸固定，靜態設定已足夠，避免引入技術範疇外的新技術（Eureka/Consul 未列於已學技術範圍）。

## 7. 身份驗證與服務間信任機制（FR-024）

- **Decision**:
  - Spring Security 搭配 BCrypt 雜湊密碼儲存於 family-service；登入成功後由 family-service 核發 **JWT**（HS256，所有後端服務共用同一組簽章密鑰，透過 config-service 集中設定），Claim 包含 `sub`(userId)、`email`、`iat`、`exp`（有效期 2 小時）。
  - 前端後續請求以 `Authorization: Bearer <JWT>` 帶入；gateway-service 於路由前以共用密鑰**本地驗證**簽章與過期時間（不需呼叫 family-service），驗證失敗回傳 `401`，驗證通過後原樣轉發該 JWT 給下游服務。
  - 家庭成員角色（ADMIN/MEMBER）與在職狀態屬於可變動狀態，不放入 JWT claim；各服務需要判斷「當下角色/是否仍在職」時（例如編輯他人支出紀錄、踢出成員），仍即時呼叫 family-service 既有的 `GET /api/families/{id}/members/{id}/authorize` 端點確認，避免已被踢出或角色已轉移的成員在 JWT 到期前仍持有舊權限。
  - 無使用者情境的內部呼叫（例如 notification-service 每月排程呼叫 family-service 取得所有 LINE 綁定清單、statistics-service 呼叫 expense-service 彙總資料）不帶使用者 JWT，改以服務間共用密鑰 Header `X-Internal-Token` 驗證呼叫來源為受信任的內部服務；此類呼叫直接以 docker-compose 服務名稱呼叫，不經過 gateway（見決策 6）。
  - 登出僅為前端捨棄 token 的客戶端行為；JWT 為無狀態設計，本階段不提供伺服器端強制註銷機制（如黑名單），屬可接受的簡化。
- **Rationale**: JWT 讓 gateway 與各服務可本地驗證使用者身分而不必每次呼叫 family-service，同時作為服務間傳遞呼叫者身分的憑證，一併解決「服務間通訊如何驗證」的需求；搭配即時角色/在職狀態查詢，避免 JWT 有效期內仍保有已被撤銷的權限。此方案亦對應開發者已熟悉 Spring Security JWT 的學習展示目標。

## 8. 每月排程觸發機制（FR-013）

- **Decision**: notification-service 使用 Spring 內建 `@Scheduled(cron = "0 0 23 L * ?")` 觸發每月最後一天 23:00 的通知流程。
- **Rationale**: Spring 5.3+ 原生支援 cron 表達式中的 `L`（月最後一天），無需引入 Quartz 等額外排程框架。

## 9. 前端資料串接方式

- **Decision**: React + TypeScript 搭配 Axios 處理 API 呼叫，並使用 TanStack Query (React Query) 管理伺服器狀態快取與重新驗證。
- **Rationale**: 屬於「已具備技術」範疇內的常見周邊工具，能簡化列表篩選、統計月份切換等需重複查詢情境的狀態管理。

## 10. 資料庫存取技術（ORM/Mapper 選型）

- **Decision**: 改用 MyBatis（`mybatis-spring-boot-starter`）取代 Spring Data JPA。各服務資料存取層採 port/adapter 分層：domain 層定義 repository 介面，infrastructure 層以 `RepositoryImpl` 實作介面並委派給 MyBatis `@Mapper` 介面；SQL 以 XML Mapper（`src/main/resources/mapper/*.xml`）撰寫。
- **Rationale**: 顯式撰寫 SQL 能更清楚掌控查詢語意與效能，避免 JPA 延遲載入、N+1 查詢等隱性行為造成不易解釋的效能問題，與原則 VI（可讀性與可解釋性優先）呼應；同時比照 task-board-practice 的 `TaskRepository`／`TaskRepositoryImpl`／`JpaTaskRepository` 分層模式（僅將最底層由 `JpaRepository` 換成 MyBatis Mapper），維持介面與實作分離的可測試性。

## 11. 資料庫技術（MSSQL → MySQL）

- **Decision**: 資料庫技術由 MSSQL 改為 MySQL，各服務 JDBC 驅動改用 `mysql-connector-j`；docker-compose 的資料庫容器改用官方 `mysql` image（例如 `mysql:8.x`）。
- **Rationale**: 與參考練習專案 task-board-practice 的技術棧一致（該專案採 MySQL + `mysql-connector-j` + `flyway-mysql`），可直接沿用其 migration 與測試作法；MySQL 為開源免費授權，對個人練習專案而言部署與授權成本較 MSSQL 低。

## 12. Schema Migration 工具

- **Decision**: 各擁有獨立資料庫的服務（family-service、expense-service、notification-service）採用 Flyway（`flyway-mysql`）管理 MySQL schema，migration script 置於 `src/main/resources/db/migration/`，命名慣例 `V{n}__{description}.sql`。
- **Rationale**: MyBatis 不像 JPA 有 `ddl-auto` 可隱含建表，需要顯式、可版本控制的 schema 定義；`flyway-mysql` 與 MySQL 相容，且與 constitution 原則 VI（可讀性、可解釋性）一致，比照 task-board-practice 的 `V1__create_initial_tables.sql` 慣例。

---

所有 Technical Context 中的 NEEDS CLARIFICATION 項目已於上述決策中解決，無未解決項目。
