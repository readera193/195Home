# 測試指南

## 測試分類

| 類型 | 位置 | 需要 Docker | 說明 |
|------|------|:-----------:|------|
| 單元測試（Service / 工具類別） | `*/src/test/java/**/*Test.java` | 否 | JUnit 5 + Mockito；覆蓋核心商業邏輯（權限判斷、金額驗證、併發鎖定、月結彙總、LINE 綁定、通知重試、分頁參數） |
| Web 層測試 | `GlobalExceptionHandlerTest`、`SecurityErrorResponseTest` | 否 | MockMvc；驗證所有錯誤皆為 Problem Details（`application/problem+json`，含 `code`） |
| 整合測試（Mapper + Flyway） | `*IntegrationTest.java` | **是** | Testcontainers 啟動真實 MySQL 8.4，驗證 migration 與 MyBatis SQL（篩選、分頁、條件式鎖定） |
| 前端測試 | `frontend/tests/` | 否 | Vitest + React Testing Library |

## 執行方式

```bash
# app-service / notification-service（於各自目錄下，使用 Maven Wrapper）
./mvnw -B test                          # 全部測試（含整合測試，需要 Docker）
./mvnw -B test -Dtest='!*IntegrationTest'   # 略過整合測試（沒有 Docker 時）
./mvnw -B verify                        # CI 使用的完整建置

# 前端（於 frontend/ 下）
npm ci
npm run lint
npm test
npm run build                           # 含 tsc 型別檢查
```

VS Code 可直接使用 `.vscode/tasks.json` 內的 task（Terminal → Run Task）。

## Profile 與設定

後端以 `SPRING_PROFILES_ACTIVE` 區分環境，**必須明確指定**：

| Profile | 用途 | 機密來源 |
|---------|------|----------|
| `dev` | 本機開發、docker-compose | `application-dev.yml` 內的開發用預設值 |
| `prod` | 正式環境（Northflank） | 全部由環境變數提供，缺少時啟動失敗；Docker image 預設即為 `prod` |
| `test` | 自動化測試 | `src/test/resources/application-test.yml` 內的假值；整合測試以 `@ActiveProfiles("test")` 啟用 |

本機不經 Docker 直接啟動服務時：

```bash
cd app-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## 手動 API 測試

`app-service/http/app-service.http`（VS Code 安裝 REST Client 擴充套件後可逐一送出請求），涵蓋註冊 → 登入 → 建立家庭 → 支付帳戶 → 支出（含分頁與篩選）→ 統計，以及各種錯誤回應範例。

## 資料庫 Migration 規則

- Flyway script 置於各服務 `src/main/resources/db/migration/`，命名 `V{n}__{description}.sql`。
- **已合併到 `dev` / `main` 的 migration 不得再修改**（Flyway 會以 checksum 檢查，已部署的資料庫會因此啟動失敗）；任何 schema 變更一律新增下一個版本號的 script（例如先 `V2` 加欄位，必要時再以 `V3` 改為 NOT NULL）。
- 新增 migration 時，同步檢查 `data-model.md` 是否需要更新，並確認 `*IntegrationTest` 能在真實 MySQL 上通過。
