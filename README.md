# 家庭共享支出平台（195Home）

家庭成員共同記帳、篩選查看、統計月結，並透過 LINE Bot 收到每月支出匯總或主動查詢。

**線上 Demo**：https://p01--app-service--pxrw8nyd5f89.code.run/ （登入頁點「Demo 體驗」即可，免帳號）
**API 文件**：https://p01--app-service--pxrw8nyd5f89.code.run/swagger-ui.html

## 畫面預覽

| 支出紀錄（篩選、分頁） | 編輯支出（先取得編輯鎖） |
|---|---|
| ![支出紀錄](docs/screenshots/03-expenses.png) | ![編輯支出](docs/screenshots/05-edit-expense.png) |

| 月結統計 | 家庭群組與成員 |
|---|---|
| ![月結統計](docs/screenshots/06-statistics.png) | ![家庭群組](docs/screenshots/07-family.png) |

## 系統架構

```mermaid
flowchart TB
    User([家庭成員<br/>瀏覽器]) -->|HTTPS| App
    Line([LINE 平台]) -->|Webhook<br/>X-Line-Signature| Notif

    subgraph App["app-service（單一 Spring Boot，唯一對外入口，含前端靜態檔案）"]
        direction LR
        Family["family 模組<br/>帳號 / 家庭群組 / 成員 / LINE 登入"]
        Expense["expense 模組<br/>支付帳戶 / 支出紀錄 / 編輯鎖"]
        Stats["statistics 模組<br/>月結彙總（即時運算，不持久化）"]
        Expense -->|FamilyAccess port<br/>同進程呼叫| Family
        Stats -->|同進程呼叫| Expense
    end

    subgraph Notif["notification-service"]
        direction LR
        Webhook["LINE Webhook<br/>關鍵字 / Rich Menu 查詢"]
        Scheduler["每月最後一天 23:00<br/>推播月結"]
    end

    Notif -->|"唯一跨進程呼叫<br/>REST + X-Internal-Token"| App
    Notif -->|Messaging API| Line

    App --- AppDB[("appdb")]
    Notif --- NotifDB[("notificationdb")]
    AppDB --- MySQL{{"單一 MySQL 執行個體"}}
    NotifDB --- MySQL
```

- **app-service**：LINE 登入、家庭群組、支付帳戶、支出紀錄、月結統計，並直接 serve 前端靜態檔案（同源部署，無需 CORS 與 Gateway）。內部依領域分 family / expense / statistics 模組。
- **notification-service**：LINE Bot Webhook、每月自動推播與關鍵字查詢；與 app-service 之間唯一透過內部 REST（`X-Internal-Token`）溝通。

## 關鍵流程：支出編輯鎖

多位家人可能同時編輯同一筆紀錄。編輯鎖以資料庫的**條件式 `UPDATE`** 實作（不需分散式鎖元件）：只有「未鎖定」或「鎖已逾時（5 分鐘）」時更新才會成功，同時搶鎖時有且只有一人取得。

```mermaid
sequenceDiagram
    actor A as 成員 A
    actor B as 成員 B
    participant API as app-service
    participant DB as MySQL

    A->>API: POST /api/expenses/{id}/lock
    API->>DB: UPDATE ... SET locked_by = A<br/>WHERE id = ? AND (locked_by IS NULL OR locked_at < now - 5 分鐘)
    DB-->>API: 影響 1 筆
    API-->>A: 200 取得鎖

    B->>API: POST /api/expenses/{id}/lock
    API->>DB: 同一條件式 UPDATE
    DB-->>API: 影響 0 筆
    API-->>B: 409 RECORD_LOCKED（編輯中）

    A->>API: PUT /api/expenses/{id}（須持有鎖）
    API->>DB: UPDATE 內容，並釋放鎖
    API-->>A: 200 更新成功
```

## API 文件

內建 OpenAPI（springdoc），可直接在 [Swagger UI](https://p01--app-service--pxrw8nyd5f89.code.run/swagger-ui.html) 試用：先呼叫 `POST /api/auth/demo` 取得 token，再按 **Authorize** 貼上。

![Swagger UI](docs/screenshots/09-swagger.png)

## 技術選型

| 項目 | 選擇 | 理由 |
|------|------|------|
| 後端 | Java 17 + Spring Boot 3.5 | 核心學習目標 |
| 資料庫存取 | MyBatis + Flyway | 顯式 SQL、可讀性優先，避免 JPA 隱性行為 |
| 身份驗證 | LINE 登入 + Spring Security + JWT | 無密碼，LINE userId 即帳號；無需獨立 Gateway |
| 服務間通訊 | 同步 REST（僅 notification → app-service） | 家庭規模不需訊息佇列 |
| 前端 | React 18 + TypeScript + Vite | 同源部署簡化設定 |
| API 錯誤格式 | RFC 9457 Problem Details | 前端以單一邏輯處理所有錯誤 |
| 部署 | GitHub Actions → GHCR → Northflank | CI 通過後自動部署 |

## 更多文件

- [開發、測試與部署](docs/DEVELOPMENT.md)
- [測試策略](TESTING.md)
- [規格與設計文件](specs/001-family-expense-tracking/)
