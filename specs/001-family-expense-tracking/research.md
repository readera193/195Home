# Phase 0 Research: 家庭共享支出平台

本文件彙整 Technical Context 中需要決策的技術項目，僅記錄最新決策結果（依 constitution 之文件記錄規範，不保留討論過程）。

## 1. 部署目標平台（CD 實際部署方式）

- **Decision**: GitHub Actions 於 CI 完成 build + test 後，CD pipeline 建置各服務 Docker 映像檔並推送至 GitHub Container Registry (GHCR)；再透過 SSH 連線到一台已安裝 Docker / Docker Compose 的可存取 VM（雲端或自有主機皆可），執行 `docker compose pull && docker compose up -d` 完成部署。
- **Rationale**: 直接沿用本機一致的 `docker-compose.yml` 設定，學習成本最低、能快速驗證整套系統可運作，且不綁定特定雲端 PaaS 廠商的專屬部署流程，符合原則 V（CI/CD 需含實際部署）與原則 VII（容器化與一鍵啟動）的精神一致性。
- **Alternatives considered**:
  - Kubernetes：複雜度超出本階段學習目標，且明確列於非目標範圍。
  - Azure App Service / AWS ECS：需額外學習雲端專屬部署模型，且 MSSQL 相容性與費用需另外處理。
  - Render / Railway 等 PaaS：對多服務 docker-compose 架構與 MSSQL 支援有限，不易對應 6 個服務 + 資料庫的完整拓樸。

## 2. LINE Messaging API 整合方式

- **Decision**: notification-service 使用 LINE 官方 `line-bot-sdk-java` 函式庫處理 Webhook 簽章驗證、訊息解析與推播發送。
- **Rationale**: 官方 SDK 已處理簽章驗證與訊息序列化細節，降低手刻整合時的安全風險（例如簽章驗證邏輯出錯導致偽造訊息被接受）。
- **Alternatives considered**: 以 Spring `WebClient` 手動呼叫 LINE REST API 並自行實作簽章驗證——維護成本高、容易出錯，不採用。

## 3. 併發編輯鎖定機制（FR-027）

- **Decision**: 於 `expense_records` 資料表新增 `locked_by_member_id`、`locked_at` 欄位。取得編輯權時以單一交易執行條件式 UPDATE：`WHERE id = ? AND (locked_by_member_id IS NULL OR locked_at < DATEADD(MINUTE, -5, SYSUTCDATETIME()))`，影響列數為 1 才視為取得鎖；儲存或取消編輯時清除鎖定欄位；鎖定 TTL 設為 5 分鐘，避免使用者異常關閉頁面導致永久鎖死。
- **Rationale**: 善用既有 MSSQL 交易機制即可達成互斥控制，不需引入 Redis 等分散式鎖工具，符合技術邊界限制（不得新增資料庫技術）與原則 VI（可讀性優先）。
- **Alternatives considered**:
  - Redis 分散式鎖：違反 constitution 技術邊界（不允許引入 MSSQL 以外的資料庫技術）。
  - MSSQL 原生 `SELECT ... FOR UPDATE` / 交易鎖：編輯行為可能持續數分鐘，長時間持有資料庫鎖易造成資源占用與交易逾時風險，不適用於此情境。

## 4. 統計資料計算策略（FR-011）

- **Decision**: statistics-service 不建立獨立資料庫，每次請求即時呼叫 expense-service 的 REST API 取得指定月份、指定家庭的支出紀錄，於記憶體中依支付帳戶彙總淨額後回傳，不做持久化快取。
- **Rationale**: 家庭規模資料量小（單一家庭每月約數十至數百筆紀錄），即時運算即可滿足 SC-003（篩選 2 秒內顯示）與統計頁效能需求，同時避免快取層與資料同步一致性問題，符合原則 VI 簡潔優先。
- **Alternatives considered**: 建立獨立統計資料庫並以排程預先彙總——對此資料規模屬過度設計，且增加跨服務資料同步的一致性風險。

## 5. Spring Cloud Config Backend

- **Decision**: 採用 native file-based backend，設定檔集中放置於專案內 `config-repo/` 目錄，由 config-service 啟動時讀取並提供給其他服務。
- **Rationale**: 展示 Spring Cloud Config 集中式設定管理的核心用法，同時避免額外維護獨立 Git repository 的複雜度。
- **Alternatives considered**: Git-backed config repository——對單一專案示範而言需額外維護獨立 repo/branch，增加不必要複雜度。

## 6. 服務間通訊與服務發現

- **Decision**: 不引入服務註冊中心（如 Eureka），服務間呼叫（gateway→各服務、statistics→expense、notification→family/statistics）採用 Spring Cloud Gateway 靜態路由設定，並透過 docker-compose 網路的服務名稱（如 `http://expense-service:8080`）直接呼叫。
- **Rationale**: docker-compose 環境下服務名稱即為穩定的 DNS 位址，家庭規模應用的服務拓樸固定，靜態設定已足夠，避免引入技術範疇外的新技術（Eureka/Consul 未列於已學技術範圍）。
- **Alternatives considered**: Netflix Eureka / Consul——constitution 技術邊界未涵蓋，對此規模非必要且增加學習成本。

## 7. 身份驗證與服務間信任機制（FR-024）

- **Decision**:
  - Spring Security 搭配 BCrypt 雜湊密碼儲存於 family-service；登入成功後由 family-service 核發 **JWT**（HS256，所有後端服務共用同一組簽章密鑰，透過 config-service 集中設定），Claim 包含 `sub`(userId)、`email`、`iat`、`exp`（有效期 2 小時）。
  - 前端後續請求以 `Authorization: Bearer <JWT>` 帶入；gateway-service 於路由前以共用密鑰**本地驗證**簽章與過期時間（不需呼叫 family-service），驗證失敗回傳 `401`，驗證通過後原樣轉發該 JWT 給下游服務。
  - 家庭成員角色（ADMIN/MEMBER）與在職狀態屬於可變動狀態，不放入 JWT claim；各服務需要判斷「當下角色/是否仍在職」時（例如編輯他人支出紀錄、踢出成員），仍即時呼叫 family-service 既有的 `GET /api/families/{id}/members/{id}/authorize` 端點確認，避免已被踢出或角色已轉移的成員在 JWT 到期前仍持有舊權限。
  - 無使用者情境的內部呼叫（例如 notification-service 每月排程呼叫 family-service 取得所有 LINE 綁定清單、statistics-service 呼叫 expense-service 彙總資料）不帶使用者 JWT，改以服務間共用密鑰 Header `X-Internal-Token` 驗證呼叫來源為受信任的內部服務；此類呼叫直接以 docker-compose 服務名稱呼叫，不經過 gateway（見決策 6）。
  - 登出僅為前端捨棄 token 的客戶端行為；JWT 為無狀態設計，本階段不提供伺服器端強制註銷機制（如黑名單），屬可接受的簡化。
- **Rationale**: JWT 讓 gateway 與各服務可本地驗證使用者身分而不必每次呼叫 family-service，同時作為服務間傳遞呼叫者身分的憑證，一併解決「服務間通訊如何驗證」的需求；搭配即時角色/在職狀態查詢，避免 JWT 有效期內仍保有已被撤銷的權限。此方案亦對應開發者已熟悉 Spring Security JWT 的學習展示目標。
- **Alternatives considered**:
  - Session Token（UUID，存於 DB）：每次請求皆需查詢 family-service 才能驗證身分，服務間呼叫仍需另外設計信任機制，較 JWT 多一層網路呼叫成本，不採用。
  - 完全無狀態 JWT（角色/在職狀態也放入 claim，不即時查詢）：實作更簡單，但踢出成員或管理者轉移後，舊 JWT 於到期前（最長 2 小時）仍可能通過角色檢查，與 FR-021、FR-028、FR-029 對狀態即時生效的隱含要求不符，不採用。
  - Spring Session + Redis：引入 Redis 違反技術邊界。

## 8. 每月排程觸發機制（FR-013）

- **Decision**: notification-service 使用 Spring 內建 `@Scheduled(cron = "0 0 23 L * ?")` 觸發每月最後一天 23:00 的通知流程。
- **Rationale**: Spring 5.3+ 原生支援 cron 表達式中的 `L`（月最後一天），無需引入 Quartz 等額外排程框架。
- **Alternatives considered**: Quartz Scheduler——功能更完整但對單一固定排程需求而言過度複雜，不採用。

## 9. 前端資料串接方式

- **Decision**: React + TypeScript 搭配 Axios 處理 API 呼叫，並使用 TanStack Query (React Query) 管理伺服器狀態快取與重新驗證。
- **Rationale**: 屬於「已具備技術」範疇內的常見周邊工具，能簡化列表篩選、統計月份切換等需重複查詢情境的狀態管理。
- **Alternatives considered**: 純 `useEffect` + `useState` 手刻資料流——對篩選/月份切換等重複查詢情境會產生較多重複邏輯，可讀性較低，不採用。

---

所有 Technical Context 中的 NEEDS CLARIFICATION 項目已於上述決策中解決，無未解決項目。
