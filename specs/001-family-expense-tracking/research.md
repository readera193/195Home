# Phase 0 Research: 家庭共享支出平台

本文件彙整 Technical Context 中需要決策的技術項目，僅記錄最新決策結果（依 constitution 之文件記錄規範，不保留討論過程）。依 constitution v3.0.0，架構已收斂為 2 個部署服務（app-service、notification-service），以下決策依此更新。

## 1. 部署目標平台（CD 實際部署方式）

- **Decision**: 部署目標為 Northflank（Sandbox 免費方案：2 個免費 service + 1 個免費 database）。GitHub Actions 於 CI 完成 build + test 後，CD pipeline 建置 app-service、notification-service 兩個 Docker 映像檔並推送至 GitHub Container Registry (GHCR)；再透過 Northflank CLI（或 Deploy API）觸發對應的兩個 Northflank service 拉取並部署最新映像檔。資料庫使用 Northflank 提供的 1 個免費 MySQL database（`appdb`、`notificationdb` 兩個 schema 建於同一個 database 執行個體內）。
- **Rationale**: 專案僅供家人自用、非高併發，Northflank 免費方案的資源額度（2 服務 + 1 資料庫）恰好對應收斂後的架構，可在零費用下取得永遠在線（無休眠）的正式環境；相較繼續維運一台自管 VM，省去 SSH 金鑰管理與作業系統維護負擔。
- **Alternatives considered**：續用原「SSH 到自管 VM 執行 docker-compose」方案——優點是不受任何平台服務數限制，但需自行維護 VM 作業系統與安全性更新，且本次決策已明確以 Northflank 免費方案為目標，故不採用。

## 2. LINE Messaging API 整合方式

- **Decision**: notification-service 使用 LINE 官方 `line-bot-sdk-java` 函式庫處理 Webhook 簽章驗證、訊息解析與推播發送。
- **Rationale**: 官方 SDK 已處理簽章驗證與訊息序列化細節，降低手刻整合時的安全風險（例如簽章驗證邏輯出錯導致偽造訊息被接受）。

## 3. 併發編輯鎖定機制（FR-027）

- **Decision**: 於 app-service 的 `expense_records` 資料表（schema `appdb`）新增 `locked_by_member_id`、`locked_at` 欄位。取得編輯權時以單一交易執行條件式 UPDATE：`WHERE id = ? AND (locked_by_member_id IS NULL OR locked_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 MINUTE))`，影響列數為 1 才視為取得鎖；儲存或取消編輯時清除鎖定欄位；鎖定 TTL 設為 5 分鐘，避免使用者異常關閉頁面導致永久鎖死。
- **Rationale**: 善用既有 MySQL 交易機制即可達成互斥控制，不需引入 Redis 等分散式鎖工具，符合技術邊界限制（不得新增資料庫技術）與原則 VI（可讀性優先）。

## 4. 統計資料計算策略（FR-011）

- **Decision**: statistics 模組不建立獨立資料表，每次請求時在 app-service 進程內**直接呼叫 expense 模組的 service 層方法**（Java 方法呼叫，非網路請求）取得指定月份、指定家庭的支出紀錄，於記憶體中依支付帳戶彙總淨額後回傳，不做持久化快取。
- **Rationale**: 服務收斂為單一 app-service 後，statistics 與 expense 同屬一個進程，原本「statistics-service 呼叫 expense-service REST API」的跨進程呼叫已無必要，直接方法呼叫更簡單、無序列化與網路延遲開銷；家庭規模資料量小（單一家庭每月約數十至數百筆紀錄），仍可滿足 SC-003（篩選 2 秒內顯示）與統計頁效能需求，同時避免快取層與資料同步一致性問題，符合原則 VI 簡潔優先。

## 5. 設定管理方式

- **Decision**: 不再使用 Spring Cloud Config Server。app-service、notification-service 各自使用 Spring Boot 標準的 `application.yml`（含 `dev`/`prod` profile），機密設定（資料庫連線字串、JWT 簽章密鑰、`X-Internal-Token` 共用密鑰、LINE channel secret/token）於正式環境改由 Northflank 的 Secret Group 掛載為環境變數覆寫，本機開發則透過 `docker-compose.yml` 的 `environment` 區塊注入。
- **Rationale**: 服務數量已收斂為 2 個，集中式 Config Server 對於「2 個服務共用少量設定」的規模而言，維運價值遠低於其占用一個獨立部署單位的成本（在 Northflank 免費方案下尤其昂貴——會多佔用寶貴的 service 額度）；Northflank 原生的環境變數/Secret 管理已可達到「機密不寫死於程式碼」的核心目的。

## 6. 服務間通訊與服務發現

- **Decision**: app-service 內部（family、expense、statistics 模組之間）一律以 Java service 層方法直接呼叫，同進程、無網路開銷，也無需服務發現機制。唯一跨進程呼叫為 **notification-service → app-service**：本機開發環境以 docker-compose 服務名稱呼叫（例如 `http://app-service:8080`）；Northflank 正式環境則呼叫 app-service 對應 Northflank service 的內部網域（Northflank 同專案內服務間可用內部位址互連，無需經過公開網際網路）。
- **Rationale**: 收斂為 2 個服務後，原本 gateway 靜態路由、docker-compose 服務名稱呼叫多個服務的複雜度已大幅簡化；不引入服務註冊中心（如 Eureka），單一固定的呼叫關係（notification → app-service）以環境變數設定目標 URL 即足夠，避免引入技術範疇外的新技術。

## 7. 身份驗證與服務間信任機制（FR-024）

- **Decision**:
  - Spring Security 搭配 BCrypt 雜湊密碼儲存於 app-service（family 模組）；登入成功後由 app-service 核發 **JWT**（HS256，簽章密鑰為 app-service 自身設定），Claim 包含 `sub`(userId)、`email`、`iat`、`exp`（有效期 2 小時）。
  - 前端後續請求以 `Authorization: Bearer <JWT>` 帶入；app-service 內建 Spring Security filter **本地驗證**簽章與過期時間，驗證失敗回傳 `401`。**不再有獨立 Gateway 進程做「本地驗證後轉發」這一層**——app-service 本身就是驗證與業務邏輯的唯一入口。
  - 家庭成員角色（ADMIN/MEMBER）與在職狀態屬於可變動狀態，不放入 JWT claim；expense、statistics 模組需要判斷「當下角色/是否仍在職」時（例如編輯他人支出紀錄、月結統計歸屬驗證），**直接呼叫 family 模組的 service 方法**確認（同進程方法呼叫，取代原本 HTTP `GET /api/families/{id}/members/{id}/authorize` 端點），避免已被踢出或角色已轉移的成員在 JWT 到期前仍持有舊權限。
  - notification-service 呼叫 app-service 的內部端點（取得所有 LINE 綁定清單、依綁定身分查詢月結彙總、消費綁定碼完成綁定）**無平台使用者 JWT**，改以服務間共用密鑰 Header `X-Internal-Token` 驗證呼叫來源為受信任的服務；此為系統中唯一保留的跨進程身分驗證機制。
  - 登出僅為前端捨棄 token 的客戶端行為；JWT 為無狀態設計，本階段不提供伺服器端強制註銷機制（如黑名單），屬可接受的簡化。
- **Rationale**: 收斂為單一 app-service 後，「gateway 本地驗證、轉發 JWT 給下游服務」的必要性已不存在——驗證與業務邏輯同在一個進程內，直接以 Spring Security filter + service 方法呼叫確認角色即可，省去一層網路呼叫與重複的 JWT 解析邏輯。`X-Internal-Token` 機制對唯一剩下的跨進程呼叫（notification-service → app-service）仍保留，因這是系統中唯一真正需要「服務對服務信任」的情境。

## 8. 每月排程觸發機制（FR-013）

- **Decision**: notification-service 使用 Spring 內建 `@Scheduled(cron = "0 0 23 L * ?")` 觸發每月最後一天 23:00 的通知流程。
- **Rationale**: Spring 5.3+ 原生支援 cron 表達式中的 `L`（月最後一天），無需引入 Quartz 等額外排程框架。

## 9. 前端資料串接方式

- **Decision**: React + TypeScript 搭配 Axios 處理 API 呼叫，並使用 TanStack Query (React Query) 管理伺服器狀態快取與重新驗證。前端建置產物直接由 app-service serve（同源部署，`/api/**` 與靜態頁面同一個 origin），呼叫 API 不需額外的 CORS 設定，也不再有獨立的 gateway 入口。
- **Rationale**: 屬於「已具備技術」範疇內的常見周邊工具，能簡化列表篩選、統計月份切換等需重複查詢情境的狀態管理；同源部署進一步降低本機與正式環境設定的落差。

## 10. 資料庫存取技術（ORM/Mapper 選型）

- **Decision**: 採用 MyBatis（`mybatis-spring-boot-starter`）。app-service、notification-service 的資料存取層皆採 port/adapter 分層：domain 層定義 repository 介面，infrastructure 層以 `RepositoryImpl` 實作介面並委派給 MyBatis `@Mapper` 介面；SQL 以 XML Mapper（`src/main/resources/mapper/*.xml`）撰寫。app-service 內依模組（family、expense）將 Mapper XML 分子目錄存放，避免單一目錄檔案混雜不同業務領域。
- **Rationale**: 顯式撰寫 SQL 能更清楚掌控查詢語意與效能，避免 JPA 延遲載入、N+1 查詢等隱性行為造成不易解釋的效能問題，與原則 VI（可讀性與可解釋性優先）呼應；以子目錄組織 Mapper XML，即使物理上是同一個 Spring Boot 專案，仍可維持模組層級的檔案邊界清晰可辨。

## 11. 資料庫技術與 Schema 切分

- **Decision**: 資料庫技術維持 MySQL，JDBC 驅動使用 `mysql-connector-j`；docker-compose 與 Northflank 皆使用單一 MySQL 執行個體。依服務資料自主權切分為 2 個 schema：`appdb`（app-service 專用，family/expense/statistics 模組共用同一個 schema，但各模組資料表各自獨立、不共用表，例如 `users`、`family_groups`、`family_members` 屬 family 模組，`payment_accounts`、`expense_records` 屬 expense 模組）、`notificationdb`（notification-service 專用）。
- **Rationale**: 服務數量收斂為 2 個後，資料庫數量也對應收斂為與部署服務一致的邊界；MySQL 為開源免費授權，且 Northflank 免費方案僅提供 1 個免費 database，2 個 schema 建於同一個執行個體內即可滿足此額度限制，同時仍維持「每個模組各自的資料表由自己的 repository 存取」之資料自主權精神。

## 12. Schema Migration 工具

- **Decision**: app-service、notification-service 皆採用 Flyway（`flyway-mysql`）管理各自 schema，migration script 置於 `src/main/resources/db/migration/`，命名慣例 `V{n}__{description}.sql`。app-service 的 migration 歷程對應單一 `appdb`，涵蓋 family/expense/statistics 三個模組的資料表建立與變更。
- **Rationale**: MyBatis 不像 JPA 有 `ddl-auto` 可隱含建表，需要顯式、可版本控制的 schema 定義；`flyway-mysql` 與 MySQL 相容，且與 constitution 原則 VI（可讀性、可解釋性）一致。

---

所有 Technical Context 中的 NEEDS CLARIFICATION 項目已於上述決策中解決，無未解決項目。
