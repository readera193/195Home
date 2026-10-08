---

description: "Task list for 家庭共享支出平台 - 核心記帳與統計功能"
---

# Tasks: 家庭共享支出平台 - 核心記帳與統計功能

**Input**: Design documents from `/specs/001-family-expense-tracking/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)、[.specify/memory/constitution.md](../../.specify/memory/constitution.md)（架構：app-service + notification-service 2 個部署服務）

**Tests**: 依 constitution 原則 IV（核心商業邏輯測試優先），下方各 User Story 皆包含針對 plan.md Technical Context 明列之核心邏輯（家庭成員權限判斷、金額整數驗證、併發鎖定、月結彙總計算、LINE 綁定唯一性、通知重試邏輯）的單元測試任務；不涵蓋樣板程式碼（getter/setter、DTO 轉換）測試。

**Organization**: 任務依 User Story 分組，各 Story 皆可獨立實作與測試。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可平行執行（不同檔案、無相依關係）
- **[Story]**：對應 spec.md 的 User Story 編號（US1~US6）
- 每項任務皆含明確檔案路徑

## Path Conventions（依 plan.md Project Structure）

- 2 個部署服務：`app-service/`、`notification-service/`
- `app-service/` 內依業務領域切套件模組：`family/`、`expense/`、`statistics/`，各自 `src/main/java/.../app/<module>/{controller,service,domain,dto}`；另有 `security/`（JWT 驗證、X-Internal-Token 驗證）、`config/`（Spring Boot 標準設定）
- 擁有獨立資料表的模組/服務（app-service 的 family、expense；notification-service）另含：
  `application/`（repository 介面/port）、`infrastructure/persistence/`（RepositoryImpl adapter +
  MyBatis Mapper 介面）、`src/main/resources/mapper/`（MyBatis XML Mapper，app-service 依模組分子目錄）、
  `src/main/resources/db/migration/`（Flyway migration script，app-service 對應 `appdb`、notification-service 對應 `notificationdb`）
- 前端：`frontend/src/{components,pages,services,hooks}`、`frontend/tests/`（建置產物複製進 `app-service/src/main/resources/static/`）
- 根目錄：`docker-compose.yml`、`.github/workflows/{ci.yml,cd.yml}`、`README.md`
- 套件命名採 `com.family195home.app`（app-service）、`com.family195home.notification`（notification-service）

---

## Phase 1: Setup（專案初始化）

**Purpose**: 建立兩個服務專案骨架與前端專案，尚不含商業邏輯

- [X] T001 建立根目錄專案結構：`app-service/`（含 `family/`、`expense/`、`statistics/`、`security/`、`config/` 套件骨架）、`notification-service/`、`frontend/` 目錄骨架（依 plan.md Project Structure）
- [X] T002 [P] 初始化 app-service Spring Boot 專案骨架（Web、Validation、MyBatis（mybatis-spring-boot-starter）、Flyway（flyway-mysql）、MySQL Connector/J（mysql-connector-j）、Spring Security、`jjwt`、Spring Boot Actuator）於 `app-service/`，套件命名 `com.family195home.app`，含 `Dockerfile`
- [X] T003 [P] 初始化 notification-service Spring Boot 專案骨架（Web、MyBatis、Flyway、MySQL Connector/J、`line-bot-sdk-java`、Spring Scheduling、Spring WebClient）於 `notification-service/`，含 `Dockerfile`
- [X] T004 [P] 初始化前端 React + TypeScript 專案（Vite）於 `frontend/`，安裝 React Router、Axios、TanStack Query (React Query)
- [X] T005 [P] 建立 app-service、notification-service 的 `application.yml` 骨架（含 DB 連線字串、JWT 簽章密鑰、`X-Internal-Token` 共用密鑰 placeholder，見 research.md 決策 5、7；正式環境改由 Northflank Secret Group 覆寫，本機開發由 docker-compose `environment` 區塊注入）
- [X] T006 建立 `docker-compose.yml` 骨架：MySQL 容器 + app-service + notification-service 兩項服務（含 `depends_on` 啟動順序）
- [X] T007 [P] 建立 `.github/workflows/ci.yml`：PR/push 時對 app-service、notification-service 執行 `./mvnw test`，對前端執行 `npm run build && npm test`（不通過測試不得合併，依 constitution 開發流程規範）
- [X] T008 [P] 為 app-service（`appdb`）、notification-service（`notificationdb`）各自建立初版 Flyway migration script `src/main/resources/db/migration/V1__create_initial_tables.sql`（MySQL DDL 語法），對應 data-model.md 定義的資料表；`expense_records` 表額外建立複合索引 `(family_group_id, payment_account_id, author_member_id, occurred_at)`，支援 FR-008/FR-009/FR-010 篩選與 SC-003（篩選後列表 2 秒內回應）

**Checkpoint**: 兩個服務骨架與前端專案就緒，可開始 Foundational 開發

---

## Phase 2: Foundational（阻擋性前置需求）

**Purpose**: 使用者帳號、登入、JWT 簽發與驗證機制、內部 API 驗證機制——所有 User Story 皆須先完成登入才能操作（FR-024），故列為阻擋性基礎設施

**⚠️ CRITICAL**: 本階段完成前，不可開始任何 User Story 的開發

- [X] T009 [P] 建立 User 資料模型（POJO）、repository 介面與 MyBatis Mapper 於 `app-service/src/main/java/com/family195home/app/family/domain/User.java`、`application/UserRepository.java`、`infrastructure/persistence/UserRepositoryImpl.java`、`infrastructure/persistence/UserMapper.java`、`resources/mapper/family/UserMapper.xml`（對應 `appdb`）
- [X] T010 [P] 實作 BCrypt 密碼雜湊設定與 JWT 簽發/驗證工具類別（HS256、Claim 含 `sub`/`email`/`iat`/`exp`，2 小時過期）於 `app-service/src/main/java/com/family195home/app/security/JwtTokenProvider.java`
- [X] T011 實作 `POST /api/users/register`、`POST /api/users/login` API（含 Email 全系統唯一性檢查，重複則回傳 `409 EMAIL_ALREADY_REGISTERED`，FR-024）於 `app-service/src/main/java/com/family195home/app/family/controller/UserController.java` 與對應 service 層（依賴 T009、T010）
- [X] T012 [P] 於 `app-service/src/main/java/com/family195home/app/security/JwtAuthFilter.java` 實作 Spring Security filter chain：對 `/api/**`（排除 `/api/users/register`、`/api/users/login`、`/api/internal/**`、靜態資源）以簽章密鑰本地驗證 JWT 與過期時間，失敗回傳 `401`（同進程完成驗證，無獨立 Gateway 進程，見 research.md 決策 7）
- [X] T013 [P] 於 `app-service/src/main/java/com/family195home/app/security/InternalTokenFilter.java` 實作 `X-Internal-Token` 驗證 filter，套用於 `/api/internal/**`，僅允許持有共用密鑰的呼叫者存取（僅供 notification-service 呼叫，見 contracts/app-service.md）
- [X] T014 [P] 前端建立登入/註冊頁面與 Axios 攔截器（自動帶入 `Authorization: Bearer <JWT>`、401 導回登入頁）於 `frontend/src/pages/LoginPage.tsx`、`frontend/src/pages/RegisterPage.tsx`、`frontend/src/services/apiClient.ts`
- [X] T015 [P] 前端建立路由骨架與受保護路由（React Router，未登入導向登入頁）於 `frontend/src/App.tsx`

**Checkpoint**: 帳號註冊/登入、JWT 簽發與本地驗證、內部 API 驗證機制就緒，可開始平行進行各 User Story 開發

---

## Phase 3: User Story 1 - 建立家庭群組並邀請成員 (Priority: P1) 🎯 MVP

**Goal**: 使用者可建立家庭群組並產生邀請碼供他人加入，管理者可移出成員、成員可離開群組，群組永遠維持一位管理者

**Independent Test**: 使用者建立家庭群組後，邀請另一位使用者以邀請碼加入，驗證雙方皆能看到彼此屬於同一家庭群組（`GET /api/families/{id}/members` 回傳兩筆在職成員）

### Implementation for User Story 1

- [X] T016 [P] [US1] 建立 FamilyGroup 資料模型（POJO）、repository 介面與 MyBatis Mapper（`name` 全系統唯一、`status` ACTIVE/DISSOLVED、`inviteCode`）於 `app-service/src/main/java/com/family195home/app/family/domain/FamilyGroup.java`、`application/FamilyGroupRepository.java`、`infrastructure/persistence/FamilyGroupRepositoryImpl.java`、`infrastructure/persistence/FamilyGroupMapper.java`、`resources/mapper/family/FamilyGroupMapper.xml`
- [X] T017 [P] [US1] 建立 FamilyMember 資料模型（POJO）、repository 介面與 MyBatis Mapper（`status` ACTIVE/LEFT/REMOVED、`role` ADMIN/MEMBER、`joinedAt`/`leftAt`）於 `app-service/src/main/java/com/family195home/app/family/domain/FamilyMember.java`、`application/FamilyMemberRepository.java`、`infrastructure/persistence/FamilyMemberRepositoryImpl.java`、`infrastructure/persistence/FamilyMemberMapper.java`、`resources/mapper/family/FamilyMemberMapper.xml`
- [X] T018 [US1] 實作 FamilyService 建立群組邏輯：群組名稱唯一性檢查（重複回傳 `409 GROUP_NAME_TAKEN`，FR-001）、建立者自動成為 `ADMIN`；加入群組邏輯：邀請碼驗證（`404 INVALID_INVITE_CODE`）、群組已解散拒絕（`409 GROUP_DISSOLVED`）、單一在職群組限制（已屬於群組者拒絕，`409 ALREADY_IN_A_GROUP`，FR-026）；若使用者對該群組已存在一筆 `status=REMOVED` 的 FamilyMember 紀錄則拒絕並回傳 `403 MEMBER_REMOVED`（FR-030）；若已存在 `status=LEFT` 的紀錄，加入時重複使用該筆紀錄並將狀態恢復為 `ACTIVE`（`role` 維持原值或預設 `MEMBER`），保留原歷史支出紀錄歸屬，不建立新的 FamilyMember（FR-025）於 `app-service/src/main/java/com/family195home/app/family/service/FamilyService.java`（依賴 T016、T017）
- [X] T019 [US1] 實作離開群組邏輯：唯一在職成員離開 → 群組標記 `DISSOLVED`（FR-018）；`ADMIN` 離開且尚有其他在職成員 → 自動將 `ADMIN` 轉移給群組內 `joinedAt` 最早的其他在職成員（FR-029）於 `FamilyService.java`
- [X] T020 [US1] 實作移出成員（kick）邏輯：限該群組 `ADMIN` 呼叫，否則回傳 `403 NOT_GROUP_ADMIN`；被移出成員狀態變更為 `REMOVED`（FR-028）；另實作 `restoreEligibility`：限 `ADMIN` 將 `REMOVED` 成員恢復為 `LEFT`，非 `REMOVED` 者回傳 `409 MEMBER_NOT_REMOVED`（FR-030）於 `FamilyService.java`
- [X] T021 [US1] 實作 `FamilyService` 內部方法 `assertMemberAuthorized(familyGroupId, callerUserId, requiredRole)`：同進程 Java 方法呼叫，供 expense、statistics 模組直接呼叫確認呼叫者當下角色與在職狀態（FR-019），未授權時拋出對應例外供上層轉換為 HTTP 錯誤碼
- [X] T022 [P] [US1] 實作 `POST /api/families`、`POST /api/families/join`、`GET /api/families/{id}/members?includeLeft=`（已離開成員標示保留於清單，FR-021；呼叫 `assertMemberAuthorized` 確認呼叫者本身即為該 `{id}` 家庭群組成員，否則回傳 `403`，FR-017）、`POST .../{memberId}/leave`、`POST .../{memberId}/kick`、`POST .../{memberId}/restore-eligibility` 端點於 `app-service/src/main/java/com/family195home/app/family/controller/FamilyController.java`（依賴 T018、T019、T020、T021）
- [X] T023 [P] [US1] 單元測試：群組名稱唯一性、單一在職群組限制（FR-026）、唯一成員離開解散群組（FR-018）、管理者自動轉移（FR-029）、kick 權限判斷與狀態變為 `REMOVED`（FR-028）、被移出成員以邀請碼加入被拒絕且管理者恢復資格後方可重新加入（FR-030）、已離開成員以邀請碼重新加入後狀態恢復為 `ACTIVE` 且沿用原 FamilyMember id（FR-025）、`assertMemberAuthorized` 各角色情境於 `app-service/src/test/java/com/family195home/app/family/FamilyServiceTest.java`
- [X] T024 [P] [US1] 前端建立家庭群組頁面（建立群組表單、顯示邀請碼/邀請連結、輸入邀請碼加入）於 `frontend/src/pages/FamilyGroupPage.tsx`
- [X] T025 [P] [US1] 前端成員列表頁面（顯示在職/已離開/已移出成員、管理者可移出成員與恢復被移出成員的加入資格、本人可離開群組）於 `frontend/src/pages/MembersPage.tsx`
- [X] T026 [US1] 前端封裝家庭群組相關 API 呼叫於 `frontend/src/services/familyApi.ts`（依賴 T014 的 apiClient）

**Checkpoint**: User Story 1 應可獨立完整運作與測試

---

## Phase 4: User Story 2 - 建立支付帳戶並記錄支出 (Priority: P1)

**Goal**: 成員可建立支付帳戶，並新增/編輯/刪除支出紀錄（含併發編輯鎖定），金額可正可負可零且為整數，備註必填

**Independent Test**: 成員建立一個支付帳戶後，新增一筆支出紀錄，驗證該紀錄正確包含備註、金額、日期與支付帳戶，且未指定日期時預設為當下

### Implementation for User Story 2

- [X] T027 [P] [US2] 建立 PaymentAccount 資料模型（POJO）、repository 介面與 MyBatis Mapper（`status` ACTIVE/DISABLED）於 `app-service/src/main/java/com/family195home/app/expense/domain/PaymentAccount.java`、`application/PaymentAccountRepository.java`、`infrastructure/persistence/PaymentAccountRepositoryImpl.java`、`infrastructure/persistence/PaymentAccountMapper.java`、`resources/mapper/expense/PaymentAccountMapper.xml`
- [X] T028 [P] [US2] 建立 ExpenseRecord 資料模型（POJO）、repository 介面與 MyBatis Mapper（含 `amount`、`note`、`occurredAt`、`lockedByMemberId`、`lockedAt` 欄位）於 `app-service/src/main/java/com/family195home/app/expense/domain/ExpenseRecord.java`、`application/ExpenseRecordRepository.java`、`infrastructure/persistence/ExpenseRecordRepositoryImpl.java`、`infrastructure/persistence/ExpenseRecordMapper.java`、`resources/mapper/expense/ExpenseRecordMapper.xml`
- [X] T029 [US2] 實作 PaymentAccountService：建立／改名支付帳戶（限 `ADMIN`，一般成員回傳 `403 NOT_GROUP_ADMIN`，FR-003，群組已解散時拒絕建立並回傳 `409 GROUP_DISSOLVED`，FR-018）、軟停用與刪除邏輯（限 `ADMIN`；`DISABLED` 後不可供新支出選用，且不允許真正刪除已使用過的帳戶，FR-022）於 `app-service/src/main/java/com/family195home/app/expense/service/PaymentAccountService.java`（依賴 T027）
- [X] T030 [P] [US2] 實作 `POST /api/accounts`、`GET /api/accounts?familyGroupId=&status=`（呼叫 `assertMemberAuthorized` 確認呼叫者屬於 `familyGroupId`，否則回傳 `403`，FR-017）、`PUT /api/accounts/{id}`、`DELETE /api/accounts/{id}`（未使用過才可刪，否則 `409 ACCOUNT_IN_USE`）、`POST /api/accounts/{id}/disable` 端點（皆限 `ADMIN`）於 `app-service/src/main/java/com/family195home/app/expense/controller/PaymentAccountController.java`（依賴 T021、T029）
- [X] T031 [US2] 實作 ExpenseService 新增邏輯：金額須為整數且可正可負可零（非整數回傳 `400 AMOUNT_MUST_BE_INTEGER`，FR-016）、備註與支付帳戶必填驗證（`400 NOTE_REQUIRED`/`400 PAYMENT_ACCOUNT_REQUIRED`）、未指定日期時預設伺服器當下時間（FR-005）、群組已解散拒絕新增（`409 GROUP_DISSOLVED`，FR-018）於 `app-service/src/main/java/com/family195home/app/expense/service/ExpenseService.java`（依賴 T028）
- [X] T032 [US2] 實作併發編輯鎖定邏輯：以條件式 UPDATE（`locked_by_member_id IS NULL OR locked_at < now-5min`）取得鎖、`POST /api/expenses/{id}/lock`（取得失敗回傳 `409 RECORD_LOCKED`）、`POST /api/expenses/{id}/unlock`（FR-027，見 research.md 決策 3）於 `ExpenseService.java`
- [X] T033 [US2] 實作編輯/刪除邏輯：直接呼叫 T021 建立的 `FamilyService.assertMemberAuthorized(...)` 方法（同進程呼叫，非 HTTP）確認操作者為該群組在職成員（管理者與一般成員權限相同，不限新增者本人，FR-019）、須已持有鎖方可操作（未持有回傳 `403 LOCK_NOT_HELD_BY_CALLER`）、`PUT /api/expenses/{id}`（成功後自動釋放鎖）、`DELETE /api/expenses/{id}` 於 `ExpenseService.java`（依賴 T021、T032）
- [X] T034 [P] [US2] 實作 `POST /api/expenses` 建立端點於 `app-service/src/main/java/com/family195home/app/expense/controller/ExpenseController.java`（依賴 T031）
- [X] T035 [US2] 實作 `PUT /api/expenses/{id}`、`DELETE /api/expenses/{id}`、`POST /api/expenses/{id}/lock`、`POST /api/expenses/{id}/unlock` 端點於 `ExpenseController.java`（依賴 T032、T033）
- [X] T036 [P] [US2] 單元測試：金額整數與正負零驗證（FR-016）、併發鎖定取得與釋放邏輯（FR-027）、編輯/刪除權限判斷（群組任一在職成員皆可、非成員拒絕，FR-019，含呼叫 `assertMemberAuthorized` 的整合行為）於 `app-service/src/test/java/com/family195home/app/expense/ExpenseServiceTest.java`
- [X] T037 [P] [US2] 前端支付帳戶管理頁面（建立、停用支付帳戶）於 `frontend/src/pages/PaymentAccountsPage.tsx`
- [X] T038 [P] [US2] 前端新增/編輯支出表單（金額、備註、支付帳戶、日期，含前端整數驗證與必填提示）於 `frontend/src/pages/ExpenseFormPage.tsx`
- [X] T039 [US2] 前端封裝支出相關 API 呼叫於 `frontend/src/services/expenseApi.ts`（依賴 T014 的 apiClient）

**Checkpoint**: User Story 1、2 應皆可獨立運作；核心記帳功能完成

---

## Phase 5: User Story 3 - 查看與篩選家庭支出紀錄 (Priority: P2)

**Goal**: 成員可查看家庭內所有支出紀錄，並依支付帳戶或成員篩選（可單獨或同時套用）

**Independent Test**: 家庭中已有多筆不同成員、不同支付帳戶的支出紀錄後，切換支付帳戶篩選與成員篩選，驗證列表結果正確對應篩選條件

### Implementation for User Story 3

- [X] T040 [US3] 實作 `GET /api/expenses?familyGroupId=&paymentAccountId=&authorMemberId=&month=` 列表與篩選邏輯（`paymentAccountId`、`authorMemberId` 可單獨或同時套用，FR-008/FR-009/FR-010；家庭範圍隔離：呼叫 T021 的 `assertMemberAuthorized` 確認呼叫者屬於 `familyGroupId`，否則回傳 `403`，FR-017）於 `app-service/src/main/java/com/family195home/app/expense/controller/ExpenseController.java` 與 `ExpenseService.java`（依賴 T021、T034）
- [X] T041 [P] [US3] 前端支出紀錄列表頁面（顯示家庭內所有成員紀錄、支付帳戶篩選下拉、成員篩選下拉，含已離開成員標示）於 `frontend/src/pages/ExpenseListPage.tsx`
- [X] T042 [US3] 前端 `expenseApi.ts` 擴充篩選查詢參數支援（依賴 T039、T040）

**Checkpoint**: User Story 1、2、3 應皆可獨立運作

---

## Phase 6: User Story 4 - 查看基本統計 (Priority: P2)

**Goal**: 成員可查看依支付帳戶彙總的月結淨額統計，並可透過月份選擇器查看任一過去月份

**Independent Test**: 家庭中已有本月多筆不同支付帳戶的支出紀錄後，開啟統計頁面，驗證各帳戶淨額加總等於本月所有紀錄金額總和

### Implementation for User Story 4

- [X] T043 [US4] 實作 StatisticsService：同進程直接呼叫 `ExpenseService` 取得指定家庭、指定月份的支出紀錄（Java 方法呼叫，見 research.md 決策 4），依 `paymentAccountId` 加總 `amount` 為 `netAmount`，當月無資料時回傳空陣列與 `totalNetAmount: 0`（非錯誤或空白畫面，FR-011）於 `app-service/src/main/java/com/family195home/app/statistics/service/StatisticsService.java`（依賴 T040）
- [X] T044 [P] [US4] 實作 `GET /api/statistics/monthly?familyGroupId=&month=` 公開端點（呼叫 T021 的 `assertMemberAuthorized` 確認呼叫者屬於該家庭群組，FR-017）於 `app-service/src/main/java/com/family195home/app/statistics/controller/StatisticsController.java`（依賴 T043、T021）
- [X] T045 [P] [US4] 單元測試：各帳戶彙總淨額加總等於當月支出紀錄總和（SC-004）、無資料月份回傳 0 而非錯誤於 `app-service/src/test/java/com/family195home/app/statistics/StatisticsServiceTest.java`
- [X] T046 [P] [US4] 前端統計頁面（月份選擇器、各支付帳戶淨額列表、總計）於 `frontend/src/pages/StatisticsPage.tsx`
- [X] T047 [US4] 前端封裝統計 API 呼叫於 `frontend/src/services/statisticsApi.ts`（依賴 T014 的 apiClient）

**Checkpoint**: 核心記帳、查看、統計功能（User Story 1-4）應皆可獨立運作，構成完整可用產品

---

## Phase 7: User Story 5 - 每月自動 LINE 通知支出匯總 (Priority: P3)

**Goal**: 成員可於平台產生 LINE 綁定碼並在 LINE Bot 完成綁定；系統於每月最後一天 23:00 自動推播當月支出匯總，失敗自動重試

**Independent Test**: 設定好家庭成員的 LINE 綁定後，於每月最後一天 23:00 觸發通知流程，驗證每位已綁定成員皆收到當月完整支出匯總訊息，且失敗重試不影響其他成員

### Implementation for User Story 5

- [X] T048 [P] [US5] 建立 LineBindingCode 資料模型（POJO）、repository 介面與 MyBatis Mapper（10 分鐘有效期、單次使用）於 `app-service/src/main/java/com/family195home/app/family/domain/LineBindingCode.java`、`application/LineBindingCodeRepository.java`、`infrastructure/persistence/LineBindingCodeRepositoryImpl.java`、`infrastructure/persistence/LineBindingCodeMapper.java`、`resources/mapper/family/LineBindingCodeMapper.xml`
- [X] T049 [P] [US5] 建立 LineBinding 資料模型（POJO）、repository 介面與 MyBatis Mapper（每個 `lineUserId` 唯一，僅能綁定一個成員身分，FR-020）於 `app-service/src/main/java/com/family195home/app/family/domain/LineBinding.java`、`application/LineBindingRepository.java`、`infrastructure/persistence/LineBindingRepositoryImpl.java`、`infrastructure/persistence/LineBindingMapper.java`、`resources/mapper/family/LineBindingMapper.xml`
- [X] T050 [US5] 實作 `LineBindingService` 產生綁定碼邏輯（限本人，10 分鐘有效，FR-023）於 `app-service/src/main/java/com/family195home/app/family/service/LineBindingService.java`（依賴 T048）
- [X] T051 [US5] 實作 `LineBindingService` 消費綁定碼邏輯（驗證碼有效性與單次使用，過期/已用拋出對應例外供 `410 CODE_EXPIRED_OR_USED`；LINE 帳號重複綁定 `409 LINE_ACCOUNT_ALREADY_BOUND`，FR-020）與依 `lineUserId` 查詢綁定身分邏輯於 `LineBindingService.java`（依賴 T049、T050）
- [X] T052 [US5] 實作 `POST /api/families/members/{id}/line-binding-codes` 公開端點；`POST /api/internal/line-bindings`、`GET /api/internal/line-bindings/by-line-user/{lineUserId}`、`GET /api/internal/line-bindings` 三個內部端點（受 T013 的 `X-Internal-Token` filter 保護，僅供 notification-service 呼叫）於 `app-service/src/main/java/com/family195home/app/family/controller/LineBindingController.java`（依賴 T051）
- [X] T053 [US5] 實作 `GET /api/internal/statistics/monthly` 內部端點（略過 `assertMemberAuthorized` 呼叫，家庭歸屬已由 notification-service 依 LINE 綁定關係決定）於 `app-service/src/main/java/com/family195home/app/statistics/controller/StatisticsController.java`（依賴 T043、T013）
- [X] T054 [P] [US5] 單元測試：綁定碼過期/已使用拒絕、同一 LINE 帳號重複綁定拒絕（FR-020）於 `app-service/src/test/java/com/family195home/app/family/LineBindingServiceTest.java`
- [X] T055 [P] [US5] 建立 NotificationLog 資料模型（POJO）、repository 介面與 MyBatis Mapper 於 `notification-service/src/main/java/com/family195home/notification/domain/NotificationLog.java`、`application/NotificationLogRepository.java`、`infrastructure/persistence/NotificationLogRepositoryImpl.java`、`infrastructure/persistence/NotificationLogMapper.java`、`resources/mapper/NotificationLogMapper.xml`
- [X] T056 [US5] 實作呼叫 app-service 內部 API 的 WebClient client 元件（請求自動帶入 `X-Internal-Token` Header，app-service base URL 由環境變數設定，本機為 docker-compose 服務名稱，正式環境為 Northflank 內部網域，見 research.md 決策 6）於 `notification-service/src/main/java/com/family195home/notification/client/AppServiceClient.java`
- [X] T057 [US5] 實作 LINE Webhook 綁定碼處理分支：`POST /api/line/webhook` 收到綁定碼格式訊息時呼叫 `AppServiceClient` 完成綁定並回覆結果（FR-023）於 `notification-service/src/main/java/com/family195home/notification/controller/LineWebhookController.java`（依賴 T052、T056）
- [X] T058 [US5] 實作每月排程通知邏輯：`@Scheduled(cron = "0 0 23 L * ?")` 呼叫 `AppServiceClient` 取得所有綁定（跨家庭）、無綁定成員的家庭略過、呼叫 `AppServiceClient` 取得當月彙總、透過 LINE Push API 發送、失敗自動重試最多 3 次仍失敗則寫入 `NotificationLog(status=FAILED)` 並不影響其他成員（FR-013）於 `notification-service/src/main/java/com/family195home/notification/scheduler/MonthlyNotificationScheduler.java`（依賴 T055、T056、T053）
- [X] T059 [P] [US5] 單元測試：發送失敗重試邏輯（達重試上限標記 FAILED、不影響其他已綁定成員，FR-013）於 `notification-service/src/test/java/com/family195home/notification/MonthlyNotificationSchedulerTest.java`
- [X] T060 [P] [US5] 前端成員設定頁面新增「產生 LINE 綁定碼」功能（顯示綁定碼與剩餘有效時間）於 `frontend/src/pages/LineBindingPage.tsx`

**Checkpoint**: User Story 1-5 應皆可獨立運作

---

## Phase 8: User Story 6 - 透過 LINE 關鍵字主動查詢月支出匯總 (Priority: P3)

**Goal**: 已綁定成員可透過 LINE 傳送「YYYY-MM」格式訊息主動查詢指定月份的家庭支出匯總

**Independent Test**: 已綁定 LINE 的成員透過 LINE 傳送指定月份（格式為「YYYY-MM」）的查詢訊息，驗證系統回覆該月份正確的支出匯總內容；未綁定帳號查詢時不洩漏任何家庭資料

### Implementation for User Story 6

- [X] T061 [US6] 擴充 LINE Webhook 邏輯：解析 `YYYY-MM` 格式訊息，呼叫 `AppServiceClient` 依 `lineUserId` 確認綁定身分（未綁定回覆「此帳號尚未綁定家庭成員身分」，不洩漏家庭資料，FR-015），已綁定則呼叫 `AppServiceClient` 取得月結彙總並回覆結果（FR-014）於 `notification-service/src/main/java/com/family195home/notification/controller/LineWebhookController.java`（依賴 T057）
- [X] T062 [US6] 擴充 LINE Webhook 邏輯：非綁定碼、非 `YYYY-MM` 格式訊息一律回覆格式提示訊息，不視為有效查詢（FR-014）於 `LineWebhookController.java`（依賴 T061）
- [X] T063 [P] [US6] 單元測試：`YYYY-MM` 格式解析正確性、未綁定帳號查詢阻擋（FR-015）、無效格式回覆提示於 `notification-service/src/test/java/com/family195home/notification/LineWebhookControllerTest.java`

**Checkpoint**: 所有 User Story（US1-US6）應皆可獨立運作

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 跨 Story 的收尾工作與部署完整性

- [X] T064 [P] 實作 `GET /api/notifications/logs?familyGroupId=&month=` 通知發送紀錄查詢端點（維運/測試用途）於 `notification-service/src/main/java/com/family195home/notification/controller/NotificationLogController.java`
- [X] T065 [P] 前端建置產物整合：由 `app-service/Dockerfile` 的 `frontend-build` stage（build context 為 repo 根目錄）執行 `npm ci && npm run build`，並將 `frontend/dist` 複製進 `app-service/src/main/resources/static/` 一併打包進映像檔；CI 的 `frontend` job 另外上傳 `frontend-dist` artifact 供檢視（依 plan.md Project Structure）
- [X] T066 [P] 完善 `docker-compose.yml`：加入 app-service、notification-service 健康檢查（Spring Boot Actuator）與 `depends_on` 條件式啟動順序、注入 `application.yml` 對應環境變數
- [X] T067 [P] 建立 `.github/workflows/cd.yml`：build app-service、notification-service 映像檔 → 推送 GHCR → 觸發 Northflank 對應兩個 service 拉取並部署最新映像檔（見 research.md 決策 1、quickstart.md「部署到 Northflank」段落）
- [X] T068 [P] 撰寫 `README.md`：系統架構圖、app-service（含 family/expense/statistics 模組）與 notification-service 職責說明、技術選型理由（含「為什麼不用 .NET」「為什麼不用訊息佇列」「為什麼只拆兩個服務，不是完整微服務」之具體回答，依 constitution 開發流程規範）
- [X] T069 依 [quickstart.md](./quickstart.md) 逐項執行 US1-US6 驗證場景，確認端對端可正常運作
- [X] T070 [P] 前端關鍵元件測試（Vitest + React Testing Library）：登入表單、支出新增表單驗證邏輯於 `frontend/tests/`
- [X] T071 [P] app-service（family、expense 模組）、notification-service 補上 MyBatis Mapper slice 測試，驗證自訂 SQL 查詢語意（例如依 `familyGroupId`/`status` 篩選、排序；分頁 SQL 見 T080），於各自 `src/test/java/.../infrastructure/persistence/`
- [X] T072 [P] app-service、notification-service 補上正式資料庫 integration 測試（Testcontainers + MySQL，比照 task-board-practice `MySqlRepositoryIntegrationTest` 模式），驗證 Flyway migration 可於真實 MySQL 執行且 Mapper SQL 與 MySQL 方言相容，於各自 `src/test/java/.../`

---

## Phase 10: 架構精進（2026-10-08，對照 task-board-practice 後的改善）

**Purpose**: 統一錯誤模型、補上分頁、強化設定與開發體驗；對應 research.md 決策 5、12、13、14、15。各項皆已含測試。

- [X] T073 [P] app-service 統一錯誤模型：`GlobalExceptionHandler` 改為繼承 `ResponseEntityExceptionHandler`，所有錯誤輸出 RFC 9457 Problem Details（`application/problem+json`，含 `code`，驗證失敗另含 `errors[]`）；新增 `ProblemDetails` 工廠，移除 `ErrorResponse`；補上 `DateTimeParseException`→400 `INVALID_DATE_FORMAT`、未預期例外→500 `INTERNAL_ERROR`（不洩漏細節）；保留 `ApiException`／`ErrorKind`，**不引入 Result 型別**，於 `app-service/src/main/java/com/family195home/app/common/`，測試 `GlobalExceptionHandlerTest.java`（research.md 決策 13）
- [X] T074 [P] Security 層 401／403 回傳 Problem Details：新增 `ProblemDetailAuthHandlers`（`AuthenticationEntryPoint` + `AccessDeniedHandler`）並於 `SecurityConfig` 註冊，於 `app-service/src/main/java/com/family195home/app/security/`，測試 `SecurityErrorResponseTest.java`（`@WebMvcTest`）
- [X] T075 notification-service 對齊錯誤契約：`ErrorBody` 改讀 Problem Details 的 `code`／`detail`（忽略其餘欄位），錯誤解析抽為 `AppServiceClient.parseError` 並補測試 `AppServiceClientErrorParsingTest.java`；`spring.mvc.problemdetails.enabled=true` 使框架層錯誤格式一致
- [X] T076 前端錯誤處理對齊：新增 `frontend/src/utils/apiError.ts`（`getErrorMessage`／`getErrorCode`）取代各頁面 `err.response?.data?.message`，移除 `catch (err: any)`；補測試 `frontend/tests/apiError.test.ts`、更新 `LoginPage.test.tsx`
- [X] T077 支出列表分頁（後端）：新增 `common/PagedResult`、`ExpenseService.listPage`（`page`／`size` 驗證，上限 100，違規 `400 INVALID_PAGE_PARAMS`）、`ExpenseRecordRepository.findPageByFilter`／`countByFilter`、`ExpenseRecordMapper.xml`（共用 `filterWhere`、`ORDER BY occurred_at DESC, id DESC`、`LIMIT/OFFSET`）、`ExpenseController` 回傳分頁結構；statistics 仍走不分頁的 `list`（research.md 決策 14）
- [X] T078 支出列表分頁（前端）：`expenseApi.listExpenses` 回傳 `PagedResponse`，`ExpenseListPage.tsx` 加上上一頁／下一頁與頁數資訊（切換篩選回第一頁），測試 `frontend/tests/ExpenseListPage.test.tsx`
- [X] T079 設定依 profile 拆分：兩個服務各自 `application.yml`（無機密預設值）＋ `application-dev.yml`／`application-prod.yml`＋測試 `application-test.yml`；Dockerfile 預設 `SPRING_PROFILES_ACTIVE=prod`、`docker-compose.yml` 覆寫為 `dev`；整合測試加 `@ActiveProfiles("test")`（research.md 決策 5）
- [X] T080 [P] 整合測試補分頁 SQL：`ExpenseRecordMapperIntegrationTest` 新增 `pageQuery_ordersNewestFirstWithStableTieBreakAndCountsAll`（Testcontainers，需 Docker）
- [X] T081 [P] 兩個服務加入 Maven Wrapper（`mvnw`、`mvnw.cmd`、`.mvn/wrapper/`，Maven 3.9.9，only-script 型）與根目錄 `.gitattributes`（`mvnw` 固定 LF）；CI 改用 `./mvnw -B verify`，使 T007、quickstart 描述的 `./mvnw` 指令成真
- [X] T082 [P] 前端工具鏈：新增 `eslint.config.js`（ESLint 9 flat config）、`npm run lint`；tsconfig 拆為 `tsconfig.app.json`／`tsconfig.node.json`（`vite.config.ts` 改由 `vitest/config` 匯入以通過型別檢查）；CI `frontend` job 改為 `npm ci` → `lint` → `build` → `test`；Dockerfile 前端 stage 改用 `npm ci`
- [X] T083 [P] 開發輔助：`app-service/http/app-service.http`（REST Client）、`.vscode/tasks.json`／`extensions.json`（`.gitignore` 僅放行這兩個檔）、根目錄 `TESTING.md`（測試分類、profile、Migration 規則）；README 同步更新
- [X] T084 規格書同步：spec.md（第八輪澄清、FR-007／SC-003、US3 情境 5、Assumptions）、contracts（共通約定、分頁、補齊 `familyGroupId` query 與漏列的錯誤代碼）、research.md（決策 5、12、13、14、15）、plan.md、quickstart.md（修正 `X-Internal-Token` 筆誤與測試指令）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：無相依，可立即開始
- **Foundational (Phase 2)**：依賴 Setup 完成，並阻擋所有 User Story 開發
- **User Stories (Phase 3-8)**：皆依賴 Foundational 完成後才可開始
  - US1、US2（P1）可平行開發（app-service 內不同套件模組：family vs expense，檔案互不重疊）
  - US3、US4（P2）依賴 US2 的 ExpenseRecord/PaymentAccount 端點已存在（T034、T030），故需在 US2 完成後開始
  - US5、US6（P3）依賴 US1（FamilyMember/`assertMemberAuthorized`）與 US4（StatisticsService）已完成；US6 直接建立於 US5 的 Webhook 控制器之上（T061 依賴 T057），為序列相依
- **Polish (Phase 9)**：依賴所有欲交付的 User Story 完成

### User Story Dependencies

- **US1 (P1)**：Foundational 完成後即可開始，無其他 Story 相依
- **US2 (P1)**：Foundational 完成後即可開始，無其他 Story 相依（可與 US1 平行）
- **US3 (P2)**：需 US2 的 `POST /api/expenses`、`POST /api/accounts` 端點已存在才有資料可篩選
- **US4 (P2)**：需 US2 的 ExpenseRecord 資料存在，StatisticsService 同進程呼叫 ExpenseService
- **US5 (P3)**：需 US1 的 FamilyMember/`assertMemberAuthorized`、US4 的 StatisticsService 已完成
- **US6 (P3)**：需 US5 的 LINE Webhook 控制器（`LineWebhookController.java`）已建立，於同檔案上擴充查詢分支

### Within Each User Story

- Entity/repository → Service 層邏輯 → Controller 端點 → 前端頁面/API 封裝
- 單元測試可與對應 Service 實作平行開發，但需在該 Service 邏輯完成後才能通過

### Parallel Opportunities

- Setup 階段所有標 [P] 任務可平行執行（T002-T005、T007-T008）
- Foundational 階段 T009/T010、T012/T013、T014/T015 可平行執行（不同檔案）
- Foundational 完成後，US1（app-service family 模組 + 前端）與 US2（app-service expense 模組 + 前端）雖同屬 app-service 專案，但套件目錄互不重疊，可由不同人平行開發
- 各 Story 內標 [P] 的 entity/測試/前端頁面任務可平行執行

---

## Parallel Example: User Story 1

```bash
# 平行建立 entity：
Task: "建立 FamilyGroup entity 與 repository 於 app-service/.../app/family/domain/FamilyGroup.java"
Task: "建立 FamilyMember entity 與 repository 於 app-service/.../app/family/domain/FamilyMember.java"

# Service/Controller 邏輯完成後，平行進行：
Task: "單元測試：群組名稱唯一性、單一在職群組限制等於 FamilyServiceTest.java"
Task: "前端家庭群組頁面於 frontend/src/pages/FamilyGroupPage.tsx"
Task: "前端成員列表頁面於 frontend/src/pages/MembersPage.tsx"
```

---

## Implementation Strategy

### MVP First（User Story 1 + 2）

1. 完成 Phase 1：Setup
2. 完成 Phase 2：Foundational（阻擋所有 Story，含帳號註冊/登入、JWT 機制與內部 API 驗證機制）
3. 完成 Phase 3：User Story 1（建立家庭群組並邀請成員）
4. 完成 Phase 4：User Story 2（建立支付帳戶並記錄支出）
5. **STOP 並驗證**：依 quickstart.md US1、US2 場景獨立測試
6. 此時已具備最小可用產品（可建立家庭、記帳）

### Incremental Delivery

1. Setup + Foundational → 基礎就緒
2. + US1 → 獨立測試 → Demo（家庭群組建立）
3. + US2 → 獨立測試 → Demo（MVP：核心記帳功能）
4. + US3 → 獨立測試 → Demo（列表與篩選）
5. + US4 → 獨立測試 → Demo（月結統計，核心加值功能完整）
6. + US5 → 獨立測試 → Demo（每月自動 LINE 通知）
7. + US6 → 獨立測試 → Demo（LINE 主動查詢，全部功能完整）
8. Polish → Northflank 部署與文件收尾

### Parallel Team Strategy

多人協作時：

1. 團隊共同完成 Setup + Foundational
2. Foundational 完成後：
   - 開發者 A：US1（app-service family 模組 + 前端家庭群組頁面）
   - 開發者 B：US2（app-service expense 模組 + 前端記帳頁面）
3. US1、US2 皆完成後，US3、US4 可平行由不同開發者接手
4. US5、US6（notification-service）因序列相依（同一 Webhook 控制器），建議由同一開發者接續完成，可與 app-service 端的開發者平行進行

---

## Notes

- [P] 任務 = 不同檔案、無相依關係
- [Story] 標籤將任務對應至特定 User Story 以利追蹤
- 每個 User Story 應可獨立完成並測試
- 測試任務對應 constitution 原則 IV 明列之核心商業邏輯，非樣板程式碼不強制測試
- 每完成一項任務或一個邏輯群組後建議提交（commit）
- 可於任一 Checkpoint 停下並獨立驗證該 Story
- 避免：模糊任務描述、同檔案衝突、破壞 Story 獨立性的跨 Story 相依
- app-service 為單一 Spring Boot 專案，family/expense/statistics 三個套件模組間**一律以 Java service 方法直接呼叫**（例如 `T033`、`T043` 依賴 `T021`），不得為模組邊界另外開發 HTTP 端點；唯一保留 HTTP 邊界的是 app-service 對外的公開/內部 API（供前端與 notification-service 呼叫）
