<!--
Sync Impact Report
- Version change: 1.0.0 → 1.1.0（新增文件決策記錄範圍規範，屬治理流程的實質補充）
- Modified principles: 無（Core Principles I~VII 內容未變更）
- Added sections:
  - 開發與品質流程（Development & Quality Workflow）新增一項規範：
    「決策過程與決策結果的記錄範圍」— 僅 spec.md 的 Clarifications session 可保留決策過程，
    其餘所有章節與所有其他文件一律只記錄最新決策結果。
- Removed sections: 無
- Templates requiring updates:
  - .specify/templates/plan-template.md ⚠ pending（尚未檢查是否引用本 constitution 的具體原則名稱，建議下次執行 /speckit-plan 時交叉確認）
  - .specify/templates/spec-template.md ✅ 無需變更（Clarifications session 由 /speckit-clarify 動態產生，範本本身不需修改）
  - .specify/templates/tasks-template.md ✅ 無需變更
  - .specify/templates/checklist-template.md ✅ 無需變更
- Follow-up TODOs: 無

---

Sync Impact Report（歷史紀錄，1.0.0 初次制定）
- Version change: [TEMPLATE] → 1.0.0（初次制定，未經正式版本，視為初始採用）
- Modified principles: 無（模板佔位符全部由具體內容取代）
- Added sections:
  - Core Principles: I~VII（微服務邊界與資料自主權、Java/Spring Boot 技術棧一致性、
    同步 REST 通訊（不用訊息佇列）、核心商業邏輯測試優先、CI/CD 需含實際部署、
    可讀性與可解釋性優先於炫技、容器化與一鍵啟動）
  - 技術範疇與邊界（Technology Scope & Boundaries）
  - 開發與品質流程（Development & Quality Workflow）
  - Governance（含修訂程序、版本規則、合規審查）
- Removed sections: 無（原檔案僅為模板佔位符）
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): 使用者未提供正式批准日期，暫以本次制定日期 2026-08-08 作為批准日，如有更早的專案啟動日期請於下次修訂時更正。
  - 部署目標平台尚未決定，將於 /speckit-plan 階段補齊技術規劃細節（本 constitution 僅約束「CI/CD 必須含實際部署」此一原則，不預先指定平台）。
-->

# 家庭共享支出平台 Constitution

## Core Principles

### I. 微服務邊界與資料自主權（Microservice Boundaries & Data Ownership）
系統依業務功能拆分為多個獨立的 Spring Boot 微服務（例如：使用者/家庭管理、支出記錄與分類、
統計報表、通知整合等），每個服務邊界必須清晰、可獨立說明其職責。每個服務擁有自己獨立的
資料存取層與資料表範圍，MUST NOT 讓不同服務直接跨界讀寫同一組資料表；服務間如需彼此的資料，
一律透過該服務對外公開的 REST API 取得，不得繞過 API 直接存取他人資料庫。
**Rationale**：跨服務直接共用 schema 是微服務展示案例中最常見的反模式，會讓「微服務」淪為
「拆開部署的單體」，無法體現服務邊界設計能力，也難以在面試中自圓其說。

### II. Java 17 + Spring Boot 技術棧一致性（Consistent Java/Spring Boot Stack）
所有後端服務一律使用 Java 17 + Spring Boot 實作，MUST NOT 引入 .NET 或其他後端語言/框架。
允許使用 Spring Boot 生態系內的常見周邊元件（如 Spring Data JPA、Spring Cloud Config、
Spring Boot Actuator、Spring Cloud Gateway 等）以展示微服務治理相關知識，但除此之外
MUST NOT 引入專案團隊完全未學過的新技術框架（例如 Kubernetes、Service Mesh）。
**Rationale**：本專案的核心學習與展示目標是 Java/Spring Boot 生態系的深度，技術棧分散會
稀釋展示重點，也會在面試時被追問「為什麼混用」而難以聚焦回答。

### III. 同步 REST 通訊，不用訊息佇列（Synchronous REST Only, No Message Queue）
服務間通訊一律採用同步 REST API，MUST NOT 引入 RabbitMQ、Kafka 等非同步訊息佇列機制。
若未來業務情境明確需要非同步處理，須先以本原則的修訂記錄變更理由，而非直接繞過此限制實作。
**Rationale**：專案範疇刻意排除訊息佇列以控制學習與展示廣度；同步 REST 已足以呈現服務拆分、
API 設計與服務間依賴管理等核心微服務能力，避免技術廣度失焦成為新技術堆疊的展示場。

### IV. 核心商業邏輯測試優先（Core Business Logic Testing）
每個服務至少須有自動化單元測試覆蓋其核心商業邏輯（例如：支出分類規則、金額計算、
家庭成員權限判斷等），測試須能在 CI pipeline 中自動執行並作為合併門檻之一。
非核心的樣板程式碼（如簡單 getter/setter、DTO 轉換）不強制要求測試覆蓋率指標。
**Rationale**：測試品質是「工程品質展示」的具體證據，但範圍應聚焦於真正有邏輯風險的程式碼，
避免為了衝測試覆蓋率數字而寫大量無意義測試，違反可讀性與可解釋性優先的原則。

### V. CI/CD 需含實際部署（CI/CD Must Include Real Deployment）
CI pipeline 須在 Pull Request 或 push 時自動觸發 build 與 test；CD pipeline 除了
image build/push 之外，MUST 包含將至少一個服務實際部署到可存取環境的步驟，
單純完成 image push 不視為完整的 CI/CD 展示。部署目標平台可於技術規劃階段（plan）決定，
但選擇原則上應優先考量學習成本低、能快速驗證整套系統可運作。
**Rationale**：本專案明確以「CI/CD 實作能力」為展示重點之一，只做到 image push 而未真正部署，
無法證明對「持續交付」全流程的掌握，也無法在展示時提供可實際操作的成果。

### VI. 可讀性與可解釋性優先於炫技（Readability & Explainability Over Over-Engineering）
商業邏輯與程式碼風格 MUST 以可讀性與可解釋性為優先考量；每個服務的關鍵設計決策
（為何拆成這個服務、為何選這個技術、有哪些取捨）須能清楚說明。MUST NOT 為了展示技術廣度
而引入不必要的抽象層、過度設計的通用框架，或使用難以向他人解釋動機的技術選型。
**Rationale**：本專案的展示優先順序為「架構設計能力 > 程式碼品質 > 功能完整度」，
過度設計或為炫技而炫技的程式碼，反而會讓面試官質疑工程判斷力，與展示目標背道而馳。

### VII. 容器化與一鍵啟動（Containerization & One-Command Startup）
每個服務 MUST 能以 Docker 容器獨立建置與啟動，並提供對應的 Dockerfile。整套系統 MUST 能透過
單一 docker-compose 設定一次啟動所有服務（含資料庫），讓任何人可在本機以最少步驟完整運行系統。
**Rationale**：一鍵可運行是技術展示專案的基本可信度門檻；評審或面試官若無法在合理時間內
啟動並操作系統，前述所有架構與品質設計都難以被實際驗證。

## 技術範疇與邊界（Technology Scope & Boundaries）

**已具備技術（可直接大量使用）**：Docker / Docker Compose、React + TypeScript（前端）、
MSSQL（資料庫）、LINE Bot（整合為通知或簡易記帳輸入管道）。這些技術不視為學習風險，
可依需求自由運用，不需額外論證選型理由。

**本次學習並應用技術（核心學習目標，須刻意展示）**：Java 17 + Spring Boot 作為所有後端服務的
實作語言；Spring Boot 生態系周邊元件（Spring Cloud Config、Spring Boot Actuator、
Spring Cloud Gateway 等）用於呈現微服務治理知識；具備實際部署動作的 CI/CD pipeline
（例如 GitHub Actions）；依業務功能拆分、以同步 REST 通訊的微服務架構。

**技術邊界（不可跨越）**：
- MUST NOT 使用 .NET 或其他非 Java 的後端語言/框架。
- 除 Java/Spring Boot 生態系相關技術外，MUST NOT 引入完全未學過的新技術或框架
  （例如訊息佇列、Kubernetes、Service Mesh）。
- 資料庫 MUST 維持 MSSQL，MUST NOT 額外引入其他資料庫技術（如 MongoDB、Redis），
  除非有明確且可清楚解釋的理由，且該理由須記錄於相關規劃文件中。
- 對外整合（如 LINE Bot 通知）MUST 封裝在合適的業務服務內，MUST NOT 為了展示技術
  而拆成不必要的獨立服務。
- 前端 MUST 透過統一的 API 入口與後端服務溝通，不得直接繞過閘道分散呼叫各服務。
- 部署目標環境於此階段尚未決定，將於後續技術規劃階段（/speckit-plan）決定；
  原則上優先選擇學習成本低、能快速驗證整套系統可運作的方案。

## 開發與品質流程（Development & Quality Workflow）

- CI pipeline 須在 PR 或 push 時自動觸發 build + test；未通過測試的變更不得合併。
- CD pipeline 須能將服務實際部署到一個可存取的環境，並可於 README 中提供存取方式或操作說明。
- README 須包含：系統架構圖、各服務職責說明、技術選型理由，且須能對應可能被追問的問題，
  至少包含「為什麼不用 .NET」與「為什麼不用訊息佇列」的具體回答。
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

**Version**: 1.1.0 | **Ratified**: 2026-08-08 | **Last Amended**: 2026-09-22
</content>
