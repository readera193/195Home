# 家庭共享支出平台（195Home）

家庭成員共同記帳、篩選查看、統計月結，並透過 LINE Bot 收到每月支出匯總或主動查詢。

## 系統架構

```text
┌─────────────────────────────────────────────────────────────┐
│                        app-service                           │
│  （單一 Spring Boot 應用，唯一對外入口，含前端靜態檔案）        │
│                                                                │
│   family 模組 ── expense 模組 ── statistics 模組               │
│  （帳號/家庭群組     （支付帳戶/         （月結彙總，            │
│   /成員/LINE綁定）    支出紀錄）          即時運算不持久化）      │
│                                                                │
│   模組間一律以 Java service 方法直接呼叫（同進程），            │
│   不透過 HTTP                                                 │
└───────────────────────────┬────────────────────────────────┘
                             │ 唯一跨進程呼叫：REST + X-Internal-Token
                             ▼
                  ┌────────────────────────┐
                  │  notification-service   │
                  │  LINE Webhook、每月排程   │
                  │  推播、關鍵字查詢          │
                  └────────────────────────┘

資料庫：單一 MySQL 執行個體，appdb（app-service）+ notificationdb（notification-service）
```

系統依 [constitution v3.0.0](.specify/memory/constitution.md) 收斂為 **2 個部署服務**：

- **app-service**：帳號註冊/登入、家庭群組、支付帳戶、支出紀錄、月結統計，並直接 serve 前端建置後的靜態檔案（同源部署，無需 CORS、無需獨立 Gateway）。內部依業務領域（family/expense/statistics）維持套件模組邊界。
- **notification-service**：LINE Bot Webhook、每月最後一天 23:00 自動推播、LINE 關鍵字查詢。與 app-service 之間唯一透過內部 REST API（`X-Internal-Token` 驗證）溝通。

完整規格與設計文件見 [`specs/001-family-expense-tracking/`](specs/001-family-expense-tracking/)（spec.md、plan.md、research.md、data-model.md、contracts/、tasks.md）。

## 技術選型

| 項目 | 選擇 | 理由 |
|------|------|------|
| 後端語言/框架 | Java 17 + Spring Boot 3.2 | constitution 核心學習目標 |
| 資料庫存取 | MyBatis + Flyway | 顯式 SQL、可讀性優先，避免 JPA 隱性行為 |
| 身份驗證 | Spring Security + JWT（jjwt） | app-service 內建本地驗證，無需獨立 Gateway |
| 服務間通訊 | 同步 REST（僅 notification→app-service 一條） | 家庭規模不需訊息佇列 |
| 前端 | React 18 + TypeScript + Vite | 已具備技術，同源部署簡化設定 |
| 部署 | Northflank Sandbox 免費方案 | 2 個免費 service + 1 個免費 database，恰好對應收斂後架構 |

### 為什麼不用 .NET？

專案的核心學習與展示目標是 Java/Spring Boot 生態系的深度；技術棧分散會稀釋展示重點，也不符合 constitution 原則 II 的技術棧一致性要求。

### 為什麼不用訊息佇列？

系統只有一條跨進程呼叫（notification-service → app-service），且屬單向、非高頻的查詢/推播情境，同步 REST 已足以呈現服務邊界與依賴管理；引入 Kafka/RabbitMQ 只會增加不必要的維運複雜度，與家庭規模的實際需求不成比例。

### 為什麼只拆兩個服務，不是完整微服務？

專案僅供家人自用、非高併發，且確定部署目標為 Northflank 免費 Sandbox 方案（2 個免費 service + 1 個免費 database）。最初規劃是 6 個獨立微服務（gateway、config server、family、expense、statistics、notification），但這遠超過免費額度，且在此規模下維持 6 個進程本身就是過度設計，違反 constitution 原則 VI（可讀性與可解釋性優先於炫技）。收斂為 2 個部署服務、以套件層級模組化取代進程層級拆分，仍可清楚展示服務邊界設計能力與「何時該拆、何時不該拆」的架構判斷，且完全落在免費方案額度內。

## 本機開發

前置需求：Docker / Docker Compose。

```bash
docker compose up -d --build
```

啟動後：
- 網頁與 API 入口：http://localhost:8080
- app-service 健康檢查：http://localhost:8080/actuator/health
- notification-service：於 docker-compose 網路內部運作；若要實測 LINE Webhook，需另外用 ngrok 等工具將 8081 對外轉發並設定 LINE Developers Console 的 Webhook URL

停止與清除：

```bash
docker compose down -v
```

## 執行測試

```bash
# app-service / notification-service（於各自服務目錄下）
mvn test

# 前端
cd frontend && npm install && npm test
```

## 部署到 Northflank

1. 於 Northflank 建立專案，開通 1 個免費 MySQL database，建立 `appdb`、`notificationdb` 兩個 schema
2. 建立 2 個 Northflank service：`app-service`、`notification-service`，各自設定對應的映像檔來源（GHCR）
3. 設定各服務所需環境變數：資料庫連線資訊、`JWT_SECRET`、`INTERNAL_TOKEN`、`LINE_CHANNEL_TOKEN`、`LINE_CHANNEL_SECRET`、`APP_SERVICE_BASE_URL`（notification-service 指向 app-service 的 Northflank 內部網域）
4. `.github/workflows/cd.yml` 於 push 到 `main` 分支時建置映像檔、推送至 GHCR，並觸發 Northflank 兩個 service 重新部署（實際觸發方式請對照 Northflank API/CLI 文件核實）

詳見 [quickstart.md](specs/001-family-expense-tracking/quickstart.md)。

## 目錄結構

```text
app-service/            # family + expense + statistics 模組 + 前端靜態檔案
notification-service/   # LINE Bot 整合
frontend/               # React + TypeScript 前端原始碼
docker-compose.yml      # 本機一鍵啟動
.github/workflows/      # CI（build+test）、CD（部署至 Northflank）
specs/                  # spec-kit 規格與設計文件
```
