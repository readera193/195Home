# Implementation Plan: 家庭共享支出平台 - 核心記帳與統計功能

**Branch**: `001-family-expense-tracking` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-family-expense-tracking/spec.md`

## Summary

建立一個家庭共享支出記帳平台，讓使用者以 Email + 密碼建立帳號，建立/加入單一家庭群組，於群組內建立個人支付帳戶並記錄金額可正可負可零的整數支出紀錄（備註必填），家庭成員可共同檢視、依支付帳戶或成員篩選紀錄，並查看依支付帳戶彙總的月結淨額統計；系統另外透過 LINE Bot 於每月最後一天 23:00 主動推播當月支出匯總，並支援成員以「YYYY-MM」訊息主動查詢。

技術方案依 constitution 採 Java 17 + Spring Boot 微服務架構，依業務職責拆分為 4 個核心服務（家庭與成員管理、支出與帳戶管理、統計彙總、LINE 通知整合）並輔以 Spring Cloud Gateway（統一入口）與 Spring Cloud Config（集中設定），前端採 React + TypeScript，資料庫統一使用 MSSQL（服務各自擁有獨立資料庫，不跨服務共用資料表），全系統以 docker-compose 一鍵啟動，並透過 GitHub Actions 建置映像檔、推送至 GHCR 後以 SSH 部署到可存取的 VM 完成 CD。

## Technical Context

**Language/Version**: Java 17（所有後端微服務，Spring Boot 3.2+）；TypeScript 5.x + React 18（前端）

**Primary Dependencies**: Spring Boot Web / Validation / Data JPA、Spring Cloud Gateway（統一 API 入口，並本地驗證 JWT）、Spring Cloud Config Server（集中設定管理，含共用 JWT 簽章密鑰）、Spring Boot Actuator（健康檢查）、Spring Security + `jjwt`（BCrypt 密碼雜湊、JWT 簽發與驗證，非完整 OAuth2 體系）、MSSQL JDBC Driver、line-bot-sdk-java（LINE Messaging API 官方 SDK）；前端：React Router、Axios、TanStack Query (React Query)

**Storage**: MSSQL Server（Docker 容器：`mcr.microsoft.com/mssql/server`）；每個擁有持久狀態的服務各自獨立資料庫（`familydb`、`expensedb`、`notificationdb`），MUST NOT 跨服務直接讀寫他人資料表；statistics-service 不建立獨立資料庫，即時彙總 expense-service 資料

**Testing**: JUnit 5 + Mockito + Spring Boot Test（後端核心商業邏輯單元/整合測試：權限判斷、金額驗證、併發鎖定、月結彙總、LINE 綁定唯一性、排程重試邏輯）；Vitest + React Testing Library（前端關鍵元件測試）

**Target Platform**: Docker 容器化服務，本機以單一 `docker-compose.yml` 一鍵啟動（含 MSSQL）；正式環境透過 GitHub Actions CI/CD 建置映像檔並推送至 GitHub Container Registry (GHCR)，以 SSH 部署到已安裝 Docker 的雲端/自有 VM 執行 `docker compose pull && up -d`

**Project Type**: web application（微服務後端：gateway-service、config-service、family-service、expense-service、statistics-service、notification-service + React 前端 frontend/）

**Performance Goals**: 家庭規模（非高併發）；篩選後列表 2 秒內回應（SC-003）；月結通知於觸發後 5 分鐘內送達所有已綁定成員（SC-005）；LINE 查詢 1 分鐘內回覆（SC-006）

**Constraints**: 金額僅接受整數（可正可負可零，不支援小數點）；同一支出紀錄同時僅允許一人編輯（DB 層邏輯鎖，5 分鐘 TTL 避免永久鎖死）；單一幣別（新台幣）；一使用者僅能同時屬於一個家庭群組；LINE 綁定碼 10 分鐘內有效且單次使用

**Scale/Scope**: 6 個 User Story、29 項功能需求、5 個核心實體；單一家庭群組規模的資料量（每月數十至數百筆支出紀錄）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 檢查結果 | 說明 |
|------|---------|------|
| I. 微服務邊界與資料自主權 | PASS | 4 個業務服務（family-service、expense-service、statistics-service、notification-service）各自擁有獨立資料庫（或無持久化），跨服務資料存取一律透過 REST API（見 `contracts/`），不共用資料表 |
| II. Java 17 + Spring Boot 技術棧一致性 | PASS | 所有後端服務統一使用 Java 17 + Spring Boot；未引入 .NET 或其他後端框架；React + TypeScript 屬「已具備技術」允許範圍 |
| III. 同步 REST 通訊，不用訊息佇列 | PASS | 服務間呼叫（statistics→expense、notification→family/statistics）一律採同步 REST（WebClient/RestTemplate），未引入 Kafka/RabbitMQ |
| IV. 核心商業邏輯測試優先 | PASS（規劃階段確認） | 已識別須測試之核心邏輯：家庭成員權限判斷、金額整數驗證、併發編輯鎖定、月結彙總計算、LINE 綁定唯一性、通知重試邏輯；實際測試將於 tasks/implement 階段落實 |
| V. CI/CD 需含實際部署 | PASS | CD pipeline 建置映像檔推送 GHCR 後，透過 SSH 部署到可存取 VM 執行 docker-compose，非僅 image push（見 research.md 決策 1） |
| VI. 可讀性與可解釋性優先於炫技 | PASS | 未引入服務註冊中心、分散式鎖、快取層等非必要基礎設施（見 research.md 決策 3、4、6）；服務拆分理由與技術選型均於 research.md 說明 |
| VII. 容器化與一鍵啟動 | PASS | 每服務含 Dockerfile；單一 `docker-compose.yml` 於本機啟動全部服務（含 MSSQL） |

**技術範疇邊界檢查**：資料庫維持單一 MSSQL（未引入 Redis/Mongo）；LINE Bot 整合封裝於 notification-service 單一服務內，未額外拆分；前端統一透過 Spring Cloud Gateway 呼叫後端，未繞過閘道分散呼叫。

無違反項目，Complexity Tracking 表格無需填寫。

**Phase 1 設計後複查**：完成 `research.md`、`data-model.md`、`contracts/`、`quickstart.md` 後重新檢視，設計內容（4 個獨立資料庫服務邊界、REST-only 服務間呼叫、DB 欄位鎖取代分散式鎖、統計即時運算不建快取層、Spring Cloud Gateway 統一入口）與上述 Constitution Check 結論一致，無新增違反項目。

## Project Structure

### Documentation (this feature)

```text
specs/001-family-expense-tracking/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── family-service.md
│   ├── expense-service.md
│   ├── statistics-service.md
│   ├── notification-service.md
│   └── gateway-routes.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
config-repo/                      # Spring Cloud Config 集中設定檔（native file-based backend）
├── gateway-service.yml
├── family-service.yml
├── expense-service.yml
├── statistics-service.yml
└── notification-service.yml

gateway-service/                  # Spring Cloud Gateway：前端與外部（LINE Webhook）的統一 API 入口
├── src/main/java/.../gateway/
└── Dockerfile

config-service/                   # Spring Cloud Config Server
├── src/main/java/.../config/
└── Dockerfile

family-service/                   # 使用者帳號、家庭群組、成員、邀請碼、LINE 綁定關係
├── src/main/java/.../family/{controller,service,repository,domain,dto}
├── src/test/java/.../family/
└── Dockerfile

expense-service/                  # 支付帳戶、支出紀錄（含併發編輯鎖定）
├── src/main/java/.../expense/{controller,service,repository,domain,dto}
├── src/test/java/.../expense/
└── Dockerfile

statistics-service/               # 依支付帳戶彙總月結淨額（無獨立資料庫，即時查詢 expense-service）
├── src/main/java/.../statistics/{controller,service,client,dto}
├── src/test/java/.../statistics/
└── Dockerfile

notification-service/             # LINE Bot 整合：Webhook、每月排程推播、關鍵字查詢、發送重試紀錄
├── src/main/java/.../notification/{controller,service,repository,domain,dto,scheduler}
├── src/test/java/.../notification/
└── Dockerfile

frontend/                         # React + TypeScript 前端
├── src/
│   ├── components/
│   ├── pages/                    # 群組建立/邀請、支付帳戶、支出紀錄列表、統計頁
│   ├── services/                 # API 客戶端（透過 gateway-service）
│   └── hooks/
└── tests/

docker-compose.yml                # 一鍵啟動：MSSQL + 全部後端服務 + frontend
.github/workflows/ci.yml          # PR/push 觸發 build + test
.github/workflows/cd.yml          # build image → push GHCR → SSH 部署至 VM
README.md
```

**Structure Decision**: 採用 Option 2（Web application）並依 constitution 的微服務要求擴充為多個獨立後端服務目錄（取代單一 `backend/`），每個服務目錄即一個獨立可建置、可容器化的 Spring Boot 專案；`frontend/` 維持標準 React + TypeScript 結構；`config-repo/` 為 Spring Cloud Config 的集中設定來源，不屬於任何單一服務。

## Complexity Tracking

無違反項目，本節無需填寫。
