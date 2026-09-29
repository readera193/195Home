<!--
Sync Impact Report
- Version change: 2.0.0 → 3.0.0（架構層級變更：對既有原則「系統依業務功能拆分為多個獨立的
  Spring Boot 微服務」做不相容於原意的重新定義——收斂為 2 個部署服務，屬 MAJOR）
- Modified principles:
  - I. 微服務邊界與資料自主權 → 更名為「服務邊界與資料自主權」：服務數量由「多個獨立微服務」
    收斂為 2 個部署服務（app-service、notification-service），改以套件（package）層級模組化
    取代進程層級拆分
  - II. Java 17 + Spring Boot 技術棧一致性：移除 Spring Cloud Gateway、Spring Cloud Config
    做為允許使用元件的範例，並新增 MUST NOT 條款禁止為湊治理知識展示而在 2 服務規模下引入
    這類僅在多服務拓樸才有意義的基礎設施
  - V. CI/CD 需含實際部署：由「至少一個服務」實際部署，改為 app-service 與 notification-service
    兩者皆須實際部署
  - VI. 可讀性與可解釋性優先於炫技：rationale 補充「服務數量收斂本身即為本原則的具體實踐」
- Modified sections:
  - 技術範疇與邊界：「本次學習並應用技術」移除 Spring Cloud Gateway/Config Server 範例；
    「技術邊界（不可跨越）」新增服務數量上限（2 個）條款、前端由 app-service 直接 serve 靜態
    檔案條款、Northflank 部署平台與免費方案額度條款；移除原「前端 MUST 透過統一的 API 入口」
    條款（因已無獨立 Gateway）
  - 開發與品質流程：README 須回答問題新增「為什麼只拆兩個服務，不是完整微服務」
  - 非目標：新增「不追求完整微服務治理基礎設施（服務註冊中心、API Gateway、集中式 Config
    Server）」
- Added sections: 無
- Removed sections: 無
- Rationale：專案僅供家人自用、非高併發，且確定部署目標為 Northflank 免費 Sandbox 方案
  （2 個免費 service + 1 個免費 database）；原本 6 個獨立微服務遠超過免費額度，維持該架構
  已不符合原則 VI（可讀性與可解釋性優先於炫技）。收斂為 2 個部署服務、以套件模組化保留業務
  邊界，仍可說明「何時該拆、何時不該拆」的架構判斷，且完全落在免費方案內。
- Templates requiring updates:
  - .specify/templates/plan-template.md ✅ 無需變更（通用範本，未寫死服務數量）
  - .specify/templates/spec-template.md ✅ 無需變更
  - .specify/templates/tasks-template.md ✅ 無需變更
  - .specify/templates/checklist-template.md ✅ 無需變更
- Follow-up TODOs：
  - specs/001-family-expense-tracking/{plan.md, research.md, data-model.md, contracts/,
    quickstart.md, tasks.md} 尚未依本次架構變更更新，須重新執行 `/speckit-plan`、
    `/speckit-tasks` 產生對應新版設計文件（詳見本次回覆的 Next Actions）
-->

# 家庭共享支出平台 Constitution

## Core Principles

### I. 服務邊界與資料自主權（Service Boundaries & Data Ownership）
系統依部署維運需求收斂為 **2 個獨立部署服務**：**app-service**（涵蓋使用者帳號、家庭群組與
成員管理、支付帳戶與支出紀錄、月結統計等所有同步互動的業務邏輯，並直接 serve 前端靜態檔案）
與 **notification-service**（LINE Bot 整合、每月排程推播、關鍵字查詢）。MUST NOT 為展示技術
廣度或模擬大型微服務系統外觀而拆分出更多獨立部署單元。app-service 內部 MUST 依業務領域
（family、expense、statistics）維持清晰的套件（package）模組邊界，各模組各自擁有 service/
repository 分層，MUST NOT 因同屬一個進程就任意跨模組直接操作彼此的資料表。notification-service
如需 app-service 的資料，一律透過 app-service 對外公開的 REST API 取得，不得繞過 API 直接
存取其資料庫。
**Rationale**：跨服務直接共用 schema、或在小規模專案中硬拆過多進程，都是反模式的兩個極端；
以套件層級模組化取代進程層級拆分，仍能清楚展示服務邊界設計能力，且不會讓「微服務」淪為
「拆開部署的單體」或「過度拆分到無法自圓其說」。

### II. Java 17 + Spring Boot 技術棧一致性（Consistent Java/Spring Boot Stack）
兩個後端服務（app-service、notification-service）一律使用 Java 17 + Spring Boot 實作，
MUST NOT 引入 .NET 或其他後端語言/框架。允許使用 Spring Boot 生態系內的常見周邊元件（如
MyBatis、Flyway、Spring Security、Spring Boot Actuator 等）。服務數量已收斂為 2 個，
MUST NOT 為展示微服務治理知識而額外引入 Spring Cloud Gateway、Spring Cloud Config Server、
服務註冊中心等僅在多服務拓樸下才有實質意義的基礎設施元件。除此之外 MUST NOT 引入專案團隊
完全未學過的新技術框架（例如 Kubernetes、Service Mesh）。
**Rationale**：本專案的核心學習與展示目標是 Java/Spring Boot 生態系的深度，以及務實的架構
判斷力（知道何時該拆分、何時不該）；技術棧分散或為了湊治理元件廣度而導入這個規模用不到的
基礎設施，反而會在面試時被追問「這個規模為什麼需要 Gateway/Config Server」而難以自圓其說。

### III. 同步 REST 通訊，不用訊息佇列（Synchronous REST Only, No Message Queue）
服務間通訊（notification-service 呼叫 app-service）一律採用同步 REST API，MUST NOT 引入
RabbitMQ、Kafka 等非同步訊息佇列機制。若未來業務情境明確需要非同步處理，須先以本原則的修訂
記錄變更理由，而非直接繞過此限制實作。
**Rationale**：專案範疇刻意排除訊息佇列以控制學習與展示廣度；同步 REST 已足以呈現服務邊界、
API 設計與服務間依賴管理等核心能力，避免技術廣度失焦成為新技術堆疊的展示場。

### IV. 核心商業邏輯測試優先（Core Business Logic Testing）
每個服務至少須有自動化單元測試覆蓋其核心商業邏輯（例如：家庭成員權限判斷、金額計算、
併發鎖定、月結彙總、LINE 綁定唯一性等），測試須能在 CI pipeline 中自動執行並作為合併門檻
之一。非核心的樣板程式碼（如簡單 getter/setter、DTO 轉換）不強制要求測試覆蓋率指標。
**Rationale**：測試品質是「工程品質展示」的具體證據，但範圍應聚焦於真正有邏輯風險的程式碼，
避免為了衝測試覆蓋率數字而寫大量無意義測試，違反可讀性與可解釋性優先的原則。

### V. CI/CD 需含實際部署（CI/CD Must Include Real Deployment）
CI pipeline 須在 Pull Request 或 push 時自動觸發 build 與 test；CD pipeline 除了 image
build/push 之外，MUST 將 app-service 與 notification-service 兩者皆實際部署到可存取環境
（見技術範疇與邊界章節之部署平台決定），單純完成 image push 不視為完整的 CI/CD 展示。
**Rationale**：本專案明確以「CI/CD 實作能力」為展示重點之一，只做到 image push 而未真正部署，
無法證明對「持續交付」全流程的掌握，也無法在展示時提供可實際操作的成果。

### VI. 可讀性與可解釋性優先於炫技（Readability & Explainability Over Over-Engineering）
商業邏輯與程式碼風格 MUST 以可讀性與可解釋性為優先考量；每個服務的關鍵設計決策（為何拆成
這個服務、為何選這個技術、有哪些取捨）須能清楚說明。MUST NOT 為了展示技術廣度而引入不必要的
抽象層、過度設計的通用框架，或使用難以向他人解釋動機的技術選型。
**Rationale**：本專案的展示優先順序為「架構設計能力 > 程式碼品質 > 功能完整度」，過度設計或
為炫技而炫技的程式碼，反而會讓面試官質疑工程判斷力，與展示目標背道而馳。將服務數量從 6 個
收斂為 2 個，正是本原則的具體實踐——依實際規模與部署限制做出恰如其分的架構決定，而非為了展示
「拆得多」而拆。

### VII. 容器化與一鍵啟動（Containerization & One-Command Startup）
每個服務 MUST 能以 Docker 容器獨立建置與啟動，並提供對應的 Dockerfile。整套系統 MUST 能透過
單一 docker-compose 設定一次啟動所有服務（含資料庫），讓任何人可在本機以最少步驟完整運行系統。
**Rationale**：一鍵可運行是技術展示專案的基本可信度門檻；評審或面試官若無法在合理時間內
啟動並操作系統，前述所有架構與品質設計都難以被實際驗證。

## 技術範疇與邊界（Technology Scope & Boundaries）

**已具備技術（可直接大量使用）**：Docker / Docker Compose、React + TypeScript（前端）、
MySQL（資料庫）、LINE Bot（整合為通知或簡易記帳輸入管道）。這些技術不視為學習風險，
可依需求自由運用，不需額外論證選型理由。

**本次學習並應用技術（核心學習目標，須刻意展示）**：Java 17 + Spring Boot 作為 app-service、
notification-service 兩個後端服務的實作語言；Spring Security（JWT 簽發與本地驗證）、
Spring Boot Actuator（健康檢查）等 Spring Boot 生態系周邊元件；具備實際部署動作的 CI/CD
pipeline（例如 GitHub Actions，部署至 Northflank）；依業務職責在單一服務內以套件模組化的
分層架構設計判斷——何時該進一步拆分成獨立服務、何時不該。

**技術邊界（不可跨越）**：
- MUST NOT 使用 .NET 或其他非 Java 的後端語言/框架。
- 除 Java/Spring Boot 生態系相關技術外，MUST NOT 引入完全未學過的新技術或框架
  （例如訊息佇列、Kubernetes、Service Mesh）。
- 資料庫 MUST 維持單一 MySQL 執行個體（依服務資料自主權切分為 `appdb`、`notificationdb`
  兩個 schema），MUST NOT 額外引入其他資料庫技術（如 MongoDB、Redis、MSSQL），除非有明確
  且可清楚解釋的理由，且該理由須記錄於相關規劃文件中。
- 對外整合（LINE Bot 通知）MUST 封裝在 notification-service 內，MUST NOT 為了展示技術
  而拆成不必要的獨立服務。
- 系統 MUST 收斂為 2 個獨立部署服務（app-service、notification-service），MUST NOT 為展示
  技術廣度或模擬大型微服務系統外觀而拆分出更多獨立部署單元；MUST NOT 引入僅在多服務拓樸下
  才有意義的治理元件（如 Spring Cloud Gateway、Spring Cloud Config Server、服務註冊中心）。
- 前端建置後的靜態檔案 MUST 直接由 app-service serve（打包進 `src/main/resources/static`），
  不得另外拆出獨立的前端部署單位；前端 MUST NOT 繞過 app-service 既定的 API 分層直接存取
  資料庫或 notification-service。
- 部署目標平台為 Northflank（Sandbox 免費方案）。MUST 確保 app-service、notification-service
  兩者的資源需求落在免費方案的 2 個 service 額度內；資料庫 MUST 使用 Northflank 提供的
  1 個免費 database，MUST NOT 額外申請第二個資料庫執行個體。

## 開發與品質流程（Development & Quality Workflow）

- CI pipeline 須在 PR 或 push 時自動觸發 build + test；未通過測試的變更不得合併。
- CD pipeline 須能將 app-service、notification-service 實際部署到 Northflank，並可於 README
  中提供存取方式或操作說明。
- README 須包含：系統架構圖、各服務職責說明、技術選型理由，且須能對應可能被追問的問題，
  至少包含「為什麼不用 .NET」「為什麼不用訊息佇列」「為什麼只拆兩個服務，不是完整微服務」
  的具體回答。
- 每個服務的關鍵設計決策（服務邊界劃分、技術選型取捨）須以文字說明保留，供未來規劃或
  面試展示時查閱，不要求正式 ADR 格式，但須清楚可讀。
- **決策過程與決策結果的記錄範圍**：僅 `spec.md` 的 Clarifications session（釐清問答小節）
  MUST 保留完整決策過程（例如討論脈絡、曾考慮但未採用的選項、問答往返記錄）。
  除此之外，spec.md 的其餘章節，以及 plan.md、tasks.md、README、本 constitution 在內的
  所有其他文件，一律 MUST NOT 保留決策過程，只記錄當下最新的決策結果；
  決策若有變更，直接更新為最新結論即可，不需保留被取代的舊版本說明。
- 程式碼審查（含自我審查）時，MUST 檢查是否違反技術邊界（原則 II、III）與資料自主權
  （原則 I），發現違反須先修正或於 constitution 修訂後才可合併。

## 非目標（Out of Scope）

- 不追求高可用、高併發等生產級規模複雜度（例如 Kubernetes、Service Mesh），除非未來
  經修訂本文件後明確列為新的學習目標。
- 不追求完整的微服務治理基礎設施（服務註冊中心、API Gateway、集中式 Config Server），
  因專案規模與部署方案（Northflank 免費方案 2 服務額度）不需要，也不具備展示價值。
- 不做多幣別、多語系等超出「家庭支出」核心情境的功能。
- LINE Bot 僅做通知或簡易記帳輸入，不發展為完整對話式記帳系統。
- 不使用非同步訊息佇列（Message Queue）作為服務間通訊機制。

## Governance

本 constitution 為本專案技術決策與範疇界定的最高依據，優先於任何個別功能規劃或臨時決定；
任何 /speckit-plan、/speckit-specify、/speckit-tasks 產出的內容如與本文件牴觸，
須以本文件為準並回頭修訂規劃內容。

**修訂程序**：任何修改本文件的提案須明確列出變更的原則或章節、變更理由，並在合併前更新
下方版本號與日期。修訂後須同步檢查 `.specify/templates/` 下的相關範本是否需要調整用詞
以保持一致（例如原則名稱變更時）。

**版本規則（語意化版本）**：
- MAJOR：移除既有原則、或對既有原則做不相容於原意的重新定義（例如放寬「不用 .NET」限制）。
- MINOR：新增原則或章節、或對既有原則做實質性擴充。
- PATCH：文字澄清、錯字修正、不影響語意的用詞調整。

**合規審查**：每次新增或調整服務、導入新技術、或設計 CI/CD 流程時，須對照本文件的
Core Principles 與技術範疇邊界逐項確認是否合規；發現複雜度或範疇擴張須先在本文件中
說明理由並完成修訂，才可繼續實作。

**Version**: 3.0.0 | **Ratified**: 2026-08-08 | **Last Amended**: 2026-09-29
</content>
