# Implementation Plan: 家庭共享支出平台 - 核心記帳與統計功能

**Branch**: `001-family-expense-tracking` | **Date**: 2026-09-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-family-expense-tracking/spec.md`

## Summary

建立一個家庭共享支出記帳平台，讓使用者以 Email + 密碼建立帳號，建立/加入單一家庭群組，於群組內建立個人支付帳戶並記錄金額可正可負可零的整數支出紀錄（備註必填），家庭成員可共同檢視、依支付帳戶或成員篩選紀錄，並查看依支付帳戶彙總的月結淨額統計；系統另外透過 LINE Bot 於每月最後一天 23:00 主動推播當月支出匯總，並支援成員以「YYYY-MM」訊息主動查詢。

技術方案依 constitution v3.0.0 採 Java 17 + Spring Boot，採 **2 個部署服務**：**app-service**（單一 Spring Boot 應用，內部依業務領域切套件模組——family、expense、statistics，並直接 serve React 前端建置後的靜態檔案）與 **notification-service**（LINE Bot 整合、每月排程推播、關鍵字查詢，維持獨立部署）。資料庫統一使用單一 MySQL 執行個體，切分為 `appdb`（family/expense/statistics 模組共用，但各模組仍各自擁有獨立資料表與 Mapper，不共用表）、`notificationdb` 兩個 schema。服務間唯一的跨進程呼叫為 notification-service 呼叫 app-service 的內部 REST API；app-service 內部模組間一律以 Java service 層方法直接呼叫，不透過 HTTP。全系統以 docker-compose 一鍵啟動本機開發環境（2 個服務 + MySQL），正式環境部署至 Northflank（Sandbox 免費方案：2 個免費 service + 1 個免費 database），CI 透過 GitHub Actions 建置與測試，CD 建置映像檔推送至 GHCR 後觸發 Northflank 兩個 service 部署最新映像檔。

## Technical Context

**Language/Version**: Java 17（app-service、notification-service，Spring Boot 3.2+）；TypeScript 5.x + React 18（前端，建置後併入 app-service）

**Primary Dependencies**: Spring Boot Web / Validation、MyBatis（`mybatis-spring-boot-starter`，資料庫存取層採 repository 介面 + RepositoryImpl adapter + MyBatis Mapper 分層）、Flyway（`flyway-mysql`，schema migration）、Spring Security + `jjwt`（BCrypt 密碼雜湊、JWT 簽發與本地驗證，於 app-service 內完成，無獨立 Gateway 進程）、Spring Boot Actuator（健康檢查）、MySQL Connector/J（`mysql-connector-j`）、line-bot-sdk-java（LINE Messaging API 官方 SDK，僅 notification-service 使用）；前端：React Router、Axios、TanStack Query (React Query)、ESLint 9（typescript-eslint）。錯誤回應格式採 Spring 6 內建的 RFC 9457 Problem Details（`ProblemDetail`），業務錯誤以 Exception 丟出，不引入 `Result<T>`（見 research.md 決策 13）。後端兩個服務皆附 Maven Wrapper（`mvnw`）。**不再使用** Spring Cloud Gateway、Spring Cloud Config Server（見 constitution v3.0.0 技術範疇與邊界）。

**Storage**: MySQL（單一執行個體，Docker 容器：官方 `mysql` image，例如 `mysql:8.x`）；依服務資料自主權切分為 2 個 schema——`appdb`（app-service 專用，family/expense/statistics 模組各自擁有獨立資料表，不跨模組共用表）、`notificationdb`（notification-service 專用）；statistics 模組不建立獨立資料表，於 app-service 進程內即時呼叫 expense 模組的 service 方法彙總

**Testing**: JUnit 5 + Mockito + Spring Boot Test（app-service、notification-service 核心商業邏輯單元/整合測試：權限判斷、金額驗證、併發鎖定、月結彙總、LINE 綁定唯一性、排程重試邏輯、分頁參數）；MockMvc 驗證錯誤回應格式（Problem Details）與 Security 401；Testcontainers + MySQL 驗證 Flyway migration 與 Mapper SQL（需要 Docker）；Vitest + React Testing Library（前端關鍵元件測試）；ESLint（前端靜態檢查，納入 CI）。測試分類與執行方式見根目錄 `TESTING.md`

**Target Platform**: Docker 容器化服務。本機開發：單一 `docker-compose.yml` 一鍵啟動 app-service、notification-service、MySQL。正式環境：Northflank（Sandbox 免費方案），app-service、notification-service 各自對應 1 個 Northflank service，MySQL 使用 Northflank 提供的 1 個免費 database；GitHub Actions 建置映像檔並推送至 GitHub Container Registry (GHCR)，再觸發 Northflank 對應 service 重新部署最新映像檔

**Project Type**: web application（2 個部署服務：app-service〔內部模組 family/expense/statistics + 內嵌前端靜態檔案〕、notification-service；前端原始碼獨立維護於 `frontend/`，建置產物併入 app-service）

**Performance Goals**: 家庭規模（非高併發）；篩選後列表 2 秒內回應（SC-003）；月結通知於觸發後 5 分鐘內送達所有已綁定成員（SC-005）；LINE 查詢 1 分鐘內回覆（SC-006）

**Constraints**: 金額僅接受整數（可正可負可零，不支援小數點）；同一支出紀錄同時僅允許一人編輯（DB 層邏輯鎖，5 分鐘 TTL 避免永久鎖死）；單一幣別（新台幣）；一使用者僅能同時屬於一個家庭群組；LINE 綁定碼 10 分鐘內有效且單次使用；app-service、notification-service 的資源需求（vCPU/記憶體）MUST 落在 Northflank Sandbox 免費方案的 2 個 service 額度內，資料庫 MUST 使用該方案的 1 個免費 database（見 constitution v3.0.0 技術範疇與邊界）

**Scale/Scope**: 6 個 User Story、29 項功能需求、5 個核心實體；單一家庭群組規模的資料量（每月數十至數百筆支出紀錄）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 檢查結果 | 說明 |
|------|---------|------|
| I. 服務邊界與資料自主權 | PASS | 2 個部署服務（app-service、notification-service）；app-service 內部依業務領域（family、expense、statistics）維持套件模組與各自的 service/repository 分層，不跨模組直接操作彼此資料表；notification-service 需要 app-service 資料時一律透過其對外公開的內部 REST API（見 `contracts/app-service.md`），不繞過 API 直接存取資料庫 |
| II. Java 17 + Spring Boot 技術棧一致性 | PASS | 兩個服務統一使用 Java 17 + Spring Boot；未引入 .NET 或其他後端框架；未引入 Spring Cloud Gateway/Config Server 等僅在多服務拓樸下才有意義的治理元件；React + TypeScript 屬「已具備技術」允許範圍 |
| III. 同步 REST 通訊，不用訊息佇列 | PASS | 唯一跨進程呼叫（notification-service → app-service）採同步 REST（WebClient/RestTemplate），未引入 Kafka/RabbitMQ；app-service 內部模組間呼叫為同進程 Java 方法呼叫，非網路通訊 |
| IV. 核心商業邏輯測試優先 | PASS（規劃階段確認） | 已識別須測試之核心邏輯：家庭成員權限判斷、金額整數驗證、併發編輯鎖定、月結彙總計算、LINE 綁定唯一性、通知重試邏輯；實際測試將於 tasks/implement 階段落實 |
| V. CI/CD 需含實際部署 | PASS | CD pipeline 建置映像檔推送 GHCR 後，觸發 Northflank 將 app-service、notification-service 兩者皆部署到可存取環境，非僅 image push（見 research.md 決策 1） |
| VI. 可讀性與可解釋性優先於炫技 | PASS | 服務數量僅 2 個，不引入服務註冊中心、Spring Cloud Gateway/Config Server 等非必要基礎設施（見 research.md 決策 5、6、7）；以套件模組化維持服務邊界，服務邊界劃分理由與技術選型均於 research.md 說明 |
| VII. 容器化與一鍵啟動 | PASS | app-service、notification-service 各含 Dockerfile；單一 `docker-compose.yml` 於本機啟動 2 個服務 + MySQL |

**技術範疇邊界檢查**：資料庫維持單一 MySQL 執行個體（appdb、notificationdb 兩個 schema，未引入 Redis/Mongo/MSSQL）；LINE Bot 整合封裝於 notification-service 單一服務內；前端建置產物直接由 app-service serve，未繞過既定分層直接存取資料庫或 notification-service；部署目標為 Northflank Sandbox 免費方案，服務數量（2 個）與資料庫數量（1 個）皆落在免費額度內。

無違反項目，Complexity Tracking 表格無需填寫。

**Phase 1 設計後複查**：完成 `research.md`、`data-model.md`、`contracts/`、`quickstart.md` 後重新檢視，設計內容（app-service 內 3 個業務模組的套件邊界、模組間直接方法呼叫、notification-service 唯一保留的跨進程 REST 呼叫、DB 欄位鎖、統計即時運算不建快取層、Northflank 部署方案）與上述 Constitution Check 結論一致，無新增違反項目。

## Project Structure

### Documentation (this feature)

```text
specs/001-family-expense-tracking/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── app-service.md
│   └── notification-service.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app-service/                       # 單一 Spring Boot 應用：family + expense + statistics 模組
                                    # + 前端靜態檔案，對外唯一入口
├── src/main/java/.../app/
│   ├── family/                    # 使用者帳號、家庭群組、成員、邀請碼、LINE 綁定關係
│   │   ├── controller/
│   │   ├── service/
│   │   ├── application/           # repository 介面（port）
│   │   ├── domain/                # 純 POJO 資料模型
│   │   ├── infrastructure/
│   │   │   └── persistence/       # RepositoryImpl（adapter）+ MyBatis Mapper 介面
│   │   └── dto/
│   ├── expense/                   # 支付帳戶、支出紀錄（含併發編輯鎖定）
│   │   ├── controller/
│   │   ├── service/
│   │   ├── application/
│   │   ├── domain/
│   │   ├── infrastructure/
│   │   │   └── persistence/
│   │   └── dto/
│   ├── statistics/                # 依支付帳戶彙總月結淨額（直接呼叫 expense 模組 service，無獨立資料表）
│   │   ├── controller/
│   │   ├── service/
│   │   └── dto/
│   ├── common/                    # 跨模組共用：ApiException/ErrorKind、GlobalExceptionHandler（Problem Details）、PagedResult
│   ├── security/                  # JWT 簽發/本地驗證、`X-Internal-Token` 驗證、401/403 的 Problem Details 輸出
│   └── config/                    # Spring Boot 標準設定類別
├── src/main/resources/
│   ├── application.yml            # 共用設定，不含機密預設值
│   ├── application-dev.yml        # 本機開發預設值（docker-compose 使用）
│   ├── application-prod.yml       # 正式環境：機密一律由 Northflank 環境變數/Secret 提供，缺少即啟動失敗
│   ├── mapper/                    # MyBatis XML Mapper（*.xml，依模組分子目錄 family/、expense/）
│   ├── db/migration/              # Flyway 版本化 migration script（V{n}__xxx.sql，對應 appdb）
│   └── static/                    # 前端 `npm run build` 產物（Docker build 的 frontend-build stage 複製進此目錄後一併打包）
├── src/test/java/.../app/{family,expense,statistics,common,security}/
├── src/test/resources/application-test.yml   # 測試用假機密
├── http/app-service.http          # VS Code REST Client 手動測試腳本
├── mvnw / mvnw.cmd / .mvn/        # Maven Wrapper
└── Dockerfile                     # 多階段：frontend-build → maven build → jre；預設 SPRING_PROFILES_ACTIVE=prod

notification-service/              # LINE Bot 整合：Webhook、每月排程推播、關鍵字查詢、發送重試紀錄
├── src/main/java/.../notification/
│   ├── config/                    # InternalTokenInterceptor（保護 /api/notifications/**）、WebConfig
│   ├── controller/
│   ├── service/
│   ├── client/                    # 呼叫 app-service 內部 REST API 的 WebClient 元件
│   ├── application/               # repository 介面（port）
│   ├── domain/                    # 純 POJO 資料模型
│   ├── infrastructure/
│   │   └── persistence/           # RepositoryImpl（adapter）+ MyBatis Mapper 介面
│   ├── dto/
│   └── scheduler/
├── src/main/resources/
│   ├── application.yml            # 共用設定，不含機密預設值
│   ├── application-dev.yml / application-prod.yml
│   ├── mapper/                    # MyBatis XML Mapper（*.xml）
│   └── db/migration/              # Flyway 版本化 migration script（V{n}__xxx.sql，對應 notificationdb）
├── src/test/java/.../notification/
├── src/test/resources/application-test.yml
├── mvnw / mvnw.cmd / .mvn/        # Maven Wrapper
└── Dockerfile

frontend/                          # React + TypeScript 前端（獨立開發用專案）
├── src/
│   ├── components/
│   ├── pages/                     # 群組建立/邀請、支付帳戶、支出紀錄列表（分頁）、統計頁
│   ├── services/                  # API 客戶端（同源呼叫 app-service，無需經過任何閘道）
│   ├── utils/                     # apiError：從 Problem Details 取出 detail / code
│   └── hooks/
├── tests/
├── eslint.config.js               # ESLint 9 flat config
└── tsconfig.json / tsconfig.app.json / tsconfig.node.json

docker-compose.yml                 # 一鍵啟動：MySQL + app-service + notification-service
.github/workflows/ci.yml           # PR/push 觸發：後端 matrix（`./mvnw verify`）＋前端（lint → build → test，並上傳 dist 產物）
.github/workflows/cd.yml           # build image → push GHCR → 觸發 Northflank 部署 app-service、notification-service
.vscode/{tasks.json,extensions.json}  # 共用的 VS Code tasks 與推薦擴充套件
TESTING.md                         # 測試分類、執行方式、profile、Migration 規則
README.md
```

**Structure Decision**: 採用 Option 2（Web application）並依 constitution 規劃為 2 個獨立可建置、可容器化的 Spring Boot 專案目錄：`app-service/`（內部以套件模組 `family`/`expense`/`statistics` 維持業務邊界）與 `notification-service/`；`frontend/` 維持標準 React + TypeScript 開發專案，其建置產物於 Docker build 階段（`app-service/Dockerfile` 的 `frontend-build` stage，build context 為 repo 根目錄）複製進 `app-service/src/main/resources/static/` 一併打包，不是獨立部署單位；設定使用各服務自身的 `application.yml`（共用）＋ `application-{dev,prod,test}.yml`（依 profile 拆分，須以 `SPRING_PROFILES_ACTIVE` 明確指定）＋ 環境變數，見 research.md 決策 5。擁有獨立資料庫的兩個服務（app-service、notification-service）的資料存取層仍採 application（repository 介面/port）與 infrastructure/persistence（RepositoryImpl adapter + MyBatis Mapper 介面）分層，SQL 以 `src/main/resources/mapper/` 下的 MyBatis XML Mapper 撰寫；資料庫 schema 以 Flyway migration script（`src/main/resources/db/migration/V{n}__{description}.sql`）版本化管理，app-service 的 migration 對應單一 `appdb` schema（各模組資料表各自獨立，不共用）。

## Complexity Tracking

無違反項目，本節無需填寫。
