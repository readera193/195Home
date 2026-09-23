---

description: "Task list for 家庭共享支出平台 - 核心記帳與統計功能"
---

# Tasks: 家庭共享支出平台 - 核心記帳與統計功能

**Input**: Design documents from `/specs/001-family-expense-tracking/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)、[.specify/memory/constitution.md](../../.specify/memory/constitution.md)

**Tests**: 依 constitution 原則 IV（核心商業邏輯測試優先），下方各 User Story 皆包含針對 plan.md Technical Context 明列之核心邏輯（家庭成員權限判斷、金額整數驗證、併發鎖定、月結彙總計算、LINE 綁定唯一性、通知重試邏輯）的單元測試任務；不涵蓋樣板程式碼（getter/setter、DTO 轉換）測試。

**Organization**: 任務依 User Story 分組，各 Story 皆可獨立實作與測試。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可平行執行（不同檔案、無相依關係）
- **[Story]**：對應 spec.md 的 User Story 編號（US1~US6）
- 每項任務皆含明確檔案路徑

## Path Conventions（依 plan.md Project Structure）

- 後端微服務：`gateway-service/`、`config-service/`、`family-service/`、`expense-service/`、`statistics-service/`、`notification-service/`，各自 `src/main/java/.../<service>/{controller,service,repository,domain,dto}`、`src/test/java/.../<service>/`
- 設定集中管理：`config-repo/`
- 前端：`frontend/src/{components,pages,services,hooks}`、`frontend/tests/`
- 根目錄：`docker-compose.yml`、`.github/workflows/{ci.yml,cd.yml}`、`README.md`
- 套件命名採 `com.family195home.<service>`（例如 `com.family195home.family`）

---

## Phase 1: Setup（專案初始化）

**Purpose**: 建立各服務專案骨架與集中設定，尚不含商業邏輯

- [ ] T001 建立根目錄專案結構：`gateway-service/`、`config-service/`、`family-service/`、`expense-service/`、`statistics-service/`、`notification-service/`、`frontend/`、`config-repo/` 目錄骨架（依 plan.md Project Structure）
- [ ] T002 [P] 建立 `config-repo/` 集中設定檔骨架：`config-repo/gateway-service.yml`、`config-repo/family-service.yml`、`config-repo/expense-service.yml`、`config-repo/statistics-service.yml`、`config-repo/notification-service.yml`（含各服務 DB 連線字串、port、JWT 共用簽章密鑰、`X-Internal-Token` 共用密鑰 placeholder，見 research.md 決策 5、7）
- [ ] T003 [P] 初始化 config-service（Spring Cloud Config Server，native file-based backend 指向 `config-repo/`）於 `config-service/`，含 `Dockerfile`
- [ ] T004 [P] 初始化 gateway-service（Spring Cloud Gateway）於 `gateway-service/`，含 `Dockerfile`
- [ ] T005 [P] 初始化 family-service Spring Boot 專案骨架（Web、Validation、Data JPA、MSSQL JDBC Driver、Spring Security、`jjwt`）於 `family-service/`，含 `Dockerfile`
- [ ] T006 [P] 初始化 expense-service Spring Boot 專案骨架（Web、Validation、Data JPA、MSSQL JDBC Driver）於 `expense-service/`，含 `Dockerfile`
- [ ] T007 [P] 初始化 statistics-service Spring Boot 專案骨架（Web、WebClient，無獨立資料庫）於 `statistics-service/`，含 `Dockerfile`
- [ ] T008 [P] 初始化 notification-service Spring Boot 專案骨架（Web、Data JPA、MSSQL JDBC Driver、`line-bot-sdk-java`、Spring Scheduling）於 `notification-service/`，含 `Dockerfile`
- [ ] T009 [P] 初始化前端 React + TypeScript 專案（Vite）於 `frontend/`，安裝 React Router、Axios、TanStack Query (React Query)
- [ ] T010 建立 `docker-compose.yml` 骨架：定義 MSSQL 容器與 config-service、gateway-service、family-service、expense-service、statistics-service、notification-service、frontend 七項服務（含服務啟動順序 `depends_on`）
- [ ] T011 [P] 建立 `.github/workflows/ci.yml`：PR/push 時對所有後端服務執行 `./mvnw test`、對前端執行 `npm test`（不通過測試不得合併，依 constitution 開發流程規範）

**Checkpoint**: 所有服務骨架與集中設定就緒，可開始 Foundational 開發

---

## Phase 2: Foundational（阻擋性前置需求）

**Purpose**: 使用者帳號、登入、JWT 簽發與驗證機制——所有 User Story 皆須先完成登入才能操作（FR-024），故列為阻擋性基礎設施

**⚠️ CRITICAL**: 本階段完成前，不可開始任何 User Story 的開發

- [ ] T012 [P] 建立 User entity 與 repository 於 `family-service/src/main/java/com/family195home/family/domain/User.java`、`repository/UserRepository.java`（對應 `familydb`）
- [ ] T013 [P] 實作 BCrypt 密碼雜湊與 JWT 簽發/驗證工具類別（HS256、Claim 含 `sub`/`email`/`iat`/`exp`，2 小時過期）於 `family-service/src/main/java/com/family195home/family/security/JwtTokenProvider.java`
- [ ] T014 實作 `POST /api/users/register`、`POST /api/users/login` API（含 Email 全系統唯一性檢查，重複則回傳 `409 EMAIL_ALREADY_REGISTERED`，FR-024）於 `family-service/src/main/java/com/family195home/family/controller/UserController.java` 與對應 service 層（依賴 T012、T013）
- [ ] T015 [P] 於 gateway-service 實作 JWT 本地驗證 GlobalFilter（以共用密鑰驗證簽章與過期時間，`/api/users/register`、`/api/users/login`、`/api/line/webhook` 除外皆需驗證，失敗回傳 `401`，並原樣轉發 JWT 給下游服務）於 `gateway-service/src/main/java/com/family195home/gateway/filter/JwtAuthGlobalFilter.java`
- [ ] T016 於 gateway-service 設定靜態路由表（依 contracts/gateway-routes.md 的 8 條路徑前綴對應各服務）於 `config-repo/gateway-service.yml`
- [ ] T017 [P] 於 expense-service、statistics-service、notification-service 各自實作 JWT 簽章本地驗證與 `X-Internal-Token` 驗證共用元件，供後續各 Story 的端點使用
- [ ] T018 [P] 前端建立登入/註冊頁面與 Axios 攔截器（自動帶入 `Authorization: Bearer <JWT>`、401 導回登入頁）於 `frontend/src/pages/LoginPage.tsx`、`frontend/src/pages/RegisterPage.tsx`、`frontend/src/services/apiClient.ts`
- [ ] T019 [P] 前端建立路由骨架與受保護路由（React Router，未登入導向登入頁）於 `frontend/src/App.tsx`

**Checkpoint**: 帳號註冊/登入、JWT 簽發與驗證機制就緒，可開始平行進行各 User Story 開發

---

## Phase 3: User Story 1 - 建立家庭群組並邀請成員 (Priority: P1) 🎯 MVP

**Goal**: 使用者可建立家庭群組並產生邀請碼供他人加入，管理者可移出成員、成員可離開群組，群組永遠維持一位管理者

**Independent Test**: 使用者建立家庭群組後，邀請另一位使用者以邀請碼加入，驗證雙方皆能看到彼此屬於同一家庭群組（`GET /api/families/{id}/members` 回傳兩筆在職成員）

### Implementation for User Story 1

- [ ] T020 [P] [US1] 建立 FamilyGroup entity 與 repository（`name` 全系統唯一、`status` ACTIVE/DISSOLVED、`inviteCode`）於 `family-service/src/main/java/com/family195home/family/domain/FamilyGroup.java`、`repository/FamilyGroupRepository.java`
- [ ] T021 [P] [US1] 建立 FamilyMember entity 與 repository（`status` ACTIVE/LEFT、`role` ADMIN/MEMBER、`joinedAt`/`leftAt`）於 `family-service/src/main/java/com/family195home/family/domain/FamilyMember.java`、`repository/FamilyMemberRepository.java`
- [ ] T022 [US1] 實作 FamilyService 建立群組邏輯：群組名稱唯一性檢查（重複回傳 `409 GROUP_NAME_TAKEN`，FR-001）、建立者自動成為 `ADMIN`；加入群組邏輯：邀請碼驗證（`404 INVALID_INVITE_CODE`）、群組已解散拒絕（`409 GROUP_DISSOLVED`）、單一在職群組限制（已屬於群組者拒絕，`409 ALREADY_IN_A_GROUP`，FR-026）於 `family-service/src/main/java/com/family195home/family/service/FamilyService.java`（依賴 T020、T021）
- [ ] T023 [US1] 實作離開群組邏輯：唯一在職成員離開 → 群組標記 `DISSOLVED`（FR-018）；`ADMIN` 離開且尚有其他在職成員 → 自動將 `ADMIN` 轉移給群組內 `joinedAt` 最早的其他在職成員（FR-029）於 `FamilyService.java`
- [ ] T024 [US1] 實作移出成員（kick）邏輯：限該群組 `ADMIN` 呼叫，否則回傳 `403 NOT_GROUP_ADMIN`；被移出成員狀態變更為 `LEFT`（FR-028）於 `FamilyService.java`
- [ ] T025 [US1] 實作 `GET /api/families/{familyGroupId}/members/{memberId}/authorize?requiredRole=` 端點，轉發呼叫者原始 JWT 判斷當下角色與在職狀態，供其他服務即時驗證權限（FR-019）於 `family-service/src/main/java/com/family195home/family/controller/FamilyController.java`
- [ ] T026 [P] [US1] 實作 `POST /api/families`、`POST /api/families/join`、`GET /api/families/{id}/members?includeLeft=`（已離開成員標示保留於清單，FR-021）、`POST .../{memberId}/leave`、`POST .../{memberId}/kick` 端點於 `FamilyController.java`（依賴 T022、T023、T024）
- [ ] T027 [P] [US1] 單元測試：群組名稱唯一性、單一在職群組限制（FR-026）、唯一成員離開解散群組（FR-018）、管理者自動轉移（FR-029）、kick 權限判斷（FR-028）於 `family-service/src/test/java/com/family195home/family/FamilyServiceTest.java`
- [ ] T028 [P] [US1] 前端建立家庭群組頁面（建立群組表單、顯示邀請碼/邀請連結、輸入邀請碼加入）於 `frontend/src/pages/FamilyGroupPage.tsx`
- [ ] T029 [P] [US1] 前端成員列表頁面（顯示在職/已離開成員、管理者可移出成員、本人可離開群組）於 `frontend/src/pages/MembersPage.tsx`
- [ ] T030 [US1] 前端封裝 family-service 相關 API 呼叫於 `frontend/src/services/familyApi.ts`（依賴 T018 的 apiClient）

**Checkpoint**: User Story 1 應可獨立完整運作與測試

---

## Phase 4: User Story 2 - 建立支付帳戶並記錄支出 (Priority: P1)

**Goal**: 成員可建立支付帳戶，並新增/編輯/刪除支出紀錄（含併發編輯鎖定），金額可正可負可零且為整數，備註必填

**Independent Test**: 成員建立一個支付帳戶後，新增一筆支出紀錄，驗證該紀錄正確包含備註、金額、日期與支付帳戶，且未指定日期時預設為當下

### Implementation for User Story 2

- [ ] T031 [P] [US2] 建立 PaymentAccount entity 與 repository（`status` ACTIVE/DISABLED）於 `expense-service/src/main/java/com/family195home/expense/domain/PaymentAccount.java`、`repository/PaymentAccountRepository.java`
- [ ] T032 [P] [US2] 建立 ExpenseRecord entity 與 repository（含 `amount`、`note`、`occurredAt`、`lockedByMemberId`、`lockedAt` 欄位）於 `expense-service/src/main/java/com/family195home/expense/domain/ExpenseRecord.java`、`repository/ExpenseRecordRepository.java`
- [ ] T033 [US2] 實作 PaymentAccountService：建立支付帳戶（FR-003）、軟停用邏輯（`DISABLED` 後不可供新支出選用，且不允許真正刪除已使用過的帳戶，FR-022）於 `expense-service/src/main/java/com/family195home/expense/service/PaymentAccountService.java`（依賴 T031）
- [ ] T034 [P] [US2] 實作 `POST /api/accounts`、`GET /api/accounts?familyGroupId=&status=`、`POST /api/accounts/{id}/disable` 端點於 `expense-service/src/main/java/com/family195home/expense/controller/PaymentAccountController.java`（依賴 T033）
- [ ] T035 [US2] 實作 ExpenseService 新增邏輯：金額須為整數且可正可負可零（非整數回傳 `400 AMOUNT_MUST_BE_INTEGER`，FR-016）、備註與支付帳戶必填驗證（`400 NOTE_REQUIRED`/`400 PAYMENT_ACCOUNT_REQUIRED`）、未指定日期時預設伺服器當下時間（FR-005）、群組已解散拒絕新增（`409 GROUP_DISSOLVED`，FR-018）於 `expense-service/src/main/java/com/family195home/expense/service/ExpenseService.java`（依賴 T032）
- [ ] T036 [US2] 實作併發編輯鎖定邏輯：以條件式 UPDATE（`locked_by_member_id IS NULL OR locked_at < now-5min`）取得鎖、`POST /api/expenses/{id}/lock`（取得失敗回傳 `409 RECORD_LOCKED`）、`POST /api/expenses/{id}/unlock`（FR-027，見 research.md 決策 3）於 `ExpenseService.java`
- [ ] T037 [US2] 實作編輯/刪除邏輯：呼叫 family-service `authorize` 端點驗證操作者為本人或該群組 `ADMIN`（FR-019）、須已持有鎖方可操作（未持有回傳 `403 LOCK_NOT_HELD_BY_CALLER`）、`PUT /api/expenses/{id}`（成功後自動釋放鎖）、`DELETE /api/expenses/{id}` 於 `ExpenseService.java`（依賴 T036）
- [ ] T038 [P] [US2] 實作 `POST /api/expenses` 建立端點於 `expense-service/src/main/java/com/family195home/expense/controller/ExpenseController.java`（依賴 T035）
- [ ] T039 [US2] 實作 `PUT /api/expenses/{id}`、`DELETE /api/expenses/{id}`、`POST /api/expenses/{id}/lock`、`POST /api/expenses/{id}/unlock` 端點於 `ExpenseController.java`（依賴 T036、T037）
- [ ] T040 [P] [US2] 單元測試：金額整數與正負零驗證（FR-016）、併發鎖定取得與釋放邏輯（FR-027）、編輯/刪除權限判斷（本人或 ADMIN，FR-019）於 `expense-service/src/test/java/com/family195home/expense/ExpenseServiceTest.java`
- [ ] T041 [P] [US2] 前端支付帳戶管理頁面（建立、停用支付帳戶）於 `frontend/src/pages/PaymentAccountsPage.tsx`
- [ ] T042 [P] [US2] 前端新增/編輯支出表單（金額、備註、支付帳戶、日期，含前端整數驗證與必填提示）於 `frontend/src/pages/ExpenseFormPage.tsx`
- [ ] T043 [US2] 前端封裝 expense-service 相關 API 呼叫於 `frontend/src/services/expenseApi.ts`（依賴 T018 的 apiClient）

**Checkpoint**: User Story 1、2 應皆可獨立運作；核心記帳功能完成

---

## Phase 5: User Story 3 - 查看與篩選家庭支出紀錄 (Priority: P2)

**Goal**: 成員可查看家庭內所有支出紀錄，並依支付帳戶或成員篩選（可單獨或同時套用）

**Independent Test**: 家庭中已有多筆不同成員、不同支付帳戶的支出紀錄後，切換支付帳戶篩選與成員篩選，驗證列表結果正確對應篩選條件

### Implementation for User Story 3

- [ ] T044 [US3] 實作 `GET /api/expenses?familyGroupId=&paymentAccountId=&authorMemberId=&month=` 列表與篩選邏輯（`paymentAccountId`、`authorMemberId` 可單獨或同時套用，FR-008/FR-009/FR-010；家庭範圍隔離，FR-017）於 `expense-service/src/main/java/com/family195home/expense/controller/ExpenseController.java` 與 `ExpenseService.java`（依賴 T038）
- [ ] T045 [P] [US3] 前端支出紀錄列表頁面（顯示家庭內所有成員紀錄、支付帳戶篩選下拉、成員篩選下拉，含已離開成員標示）於 `frontend/src/pages/ExpenseListPage.tsx`
- [ ] T046 [US3] 前端 `expenseApi.ts` 擴充篩選查詢參數支援（依賴 T043、T044）

**Checkpoint**: User Story 1、2、3 應皆可獨立運作

---

## Phase 6: User Story 4 - 查看基本統計 (Priority: P2)

**Goal**: 成員可查看依支付帳戶彙總的月結淨額統計，並可透過月份選擇器查看任一過去月份

**Independent Test**: 家庭中已有本月多筆不同支付帳戶的支出紀錄後，開啟統計頁面，驗證各帳戶淨額加總等於本月所有紀錄金額總和

### Implementation for User Story 4

- [ ] T047 [US4] 實作 statistics-service 呼叫 expense-service `GET /api/expenses?familyGroupId=&month=` 取得指定月份紀錄的 WebClient 元件於 `statistics-service/src/main/java/com/family195home/statistics/client/ExpenseServiceClient.java`
- [ ] T048 [US4] 實作月結彙總邏輯：依 `paymentAccountId` 加總 `amount` 為 `netAmount`，當月無資料時回傳空陣列與 `totalNetAmount: 0`（非錯誤或空白畫面，FR-011）於 `statistics-service/src/main/java/com/family195home/statistics/service/StatisticsService.java`（依賴 T047）
- [ ] T049 [P] [US4] 實作 `GET /api/statistics/monthly?familyGroupId=&month=` 端點（轉發呼叫者 JWT 呼叫 family-service `authorize` 確認呼叫者屬於該家庭群組，FR-017）於 `statistics-service/src/main/java/com/family195home/statistics/controller/StatisticsController.java`（依賴 T048）
- [ ] T050 [P] [US4] 單元測試：各帳戶彙總淨額加總等於當月支出紀錄總和（SC-004）、無資料月份回傳 0 而非錯誤於 `statistics-service/src/test/java/com/family195home/statistics/StatisticsServiceTest.java`
- [ ] T051 [P] [US4] 前端統計頁面（月份選擇器、各支付帳戶淨額列表、總計）於 `frontend/src/pages/StatisticsPage.tsx`
- [ ] T052 [US4] 前端封裝 statistics-service API 呼叫於 `frontend/src/services/statisticsApi.ts`（依賴 T018 的 apiClient）

**Checkpoint**: 核心記帳、查看、統計功能（User Story 1-4）應皆可獨立運作，構成完整可用產品

---

## Phase 7: User Story 5 - 每月自動 LINE 通知支出匯總 (Priority: P3)

**Goal**: 成員可於平台產生 LINE 綁定碼並在 LINE Bot 完成綁定；系統於每月最後一天 23:00 自動推播當月支出匯總，失敗自動重試

**Independent Test**: 設定好家庭成員的 LINE 綁定後，於每月最後一天 23:00 觸發通知流程，驗證每位已綁定成員皆收到當月完整支出匯總訊息，且失敗重試不影響其他成員

### Implementation for User Story 5

- [ ] T053 [P] [US5] 建立 LineBindingCode entity 與 repository（10 分鐘有效期、單次使用）於 `family-service/src/main/java/com/family195home/family/domain/LineBindingCode.java`、`repository/LineBindingCodeRepository.java`
- [ ] T054 [P] [US5] 建立 LineBinding entity 與 repository（每個 `lineUserId` 唯一，僅能綁定一個成員身分，FR-020）於 `family-service/src/main/java/com/family195home/family/domain/LineBinding.java`、`repository/LineBindingRepository.java`
- [ ] T055 [US5] 實作 `POST /api/families/members/{id}/line-binding-codes` 產生綁定碼邏輯（限本人，10 分鐘有效，FR-023）於 `family-service/src/main/java/com/family195home/family/service/LineBindingService.java`（依賴 T053）
- [ ] T056 [US5] 實作 `POST /api/line-bindings`（驗證碼有效性與單次使用，過期/已用回傳 `410 CODE_EXPIRED_OR_USED`；LINE 帳號重複綁定回傳 `409 LINE_ACCOUNT_ALREADY_BOUND`，FR-020）、`GET /api/line-bindings/by-line-user/{lineUserId}`（`404 NOT_BOUND`）、`GET /api/line-bindings`（皆以 `X-Internal-Token` 驗證）於 `family-service/src/main/java/com/family195home/family/controller/LineBindingController.java`（依賴 T054、T055）
- [ ] T057 [P] [US5] 單元測試：綁定碼過期/已使用拒絕、同一 LINE 帳號重複綁定拒絕（FR-020）於 `family-service/src/test/java/com/family195home/family/LineBindingServiceTest.java`
- [ ] T058 [P] [US5] 建立 NotificationLog entity 與 repository 於 `notification-service/src/main/java/com/family195home/notification/domain/NotificationLog.java`、`repository/NotificationLogRepository.java`
- [ ] T059 [US5] 實作 LINE Webhook 綁定碼處理分支：`POST /api/line/webhook` 收到綁定碼格式訊息時呼叫 family-service 完成綁定並回覆結果（FR-023）於 `notification-service/src/main/java/com/family195home/notification/controller/LineWebhookController.java`（依賴 T056）
- [ ] T060 [US5] 實作每月排程通知邏輯：`@Scheduled(cron = "0 0 23 L * ?")` 呼叫 family-service `GET /api/line-bindings` 取得所有綁定（跨家庭）、無綁定成員的家庭略過、呼叫 statistics-service 取得當月彙總、透過 LINE Push API 發送、失敗自動重試最多 3 次仍失敗則寫入 `NotificationLog(status=FAILED)` 並不影響其他成員（FR-013）於 `notification-service/src/main/java/com/family195home/notification/scheduler/MonthlyNotificationScheduler.java`（依賴 T058）
- [ ] T061 [P] [US5] 單元測試：發送失敗重試邏輯（達重試上限標記 FAILED、不影響其他已綁定成員，FR-013）於 `notification-service/src/test/java/com/family195home/notification/MonthlyNotificationSchedulerTest.java`
- [ ] T062 [P] [US5] 前端成員設定頁面新增「產生 LINE 綁定碼」功能（顯示綁定碼與剩餘有效時間）於 `frontend/src/pages/LineBindingPage.tsx`

**Checkpoint**: User Story 1-5 應皆可獨立運作

---

## Phase 8: User Story 6 - 透過 LINE 關鍵字主動查詢月支出匯總 (Priority: P3)

**Goal**: 已綁定成員可透過 LINE 傳送「YYYY-MM」格式訊息主動查詢指定月份的家庭支出匯總

**Independent Test**: 已綁定 LINE 的成員透過 LINE 傳送指定月份（格式為「YYYY-MM」）的查詢訊息，驗證系統回覆該月份正確的支出匯總內容；未綁定帳號查詢時不洩漏任何家庭資料

### Implementation for User Story 6

- [ ] T063 [US6] 擴充 LINE Webhook 邏輯：解析 `YYYY-MM` 格式訊息，呼叫 family-service `GET /api/line-bindings/by-line-user/{lineUserId}` 確認綁定身分（未綁定回覆「此帳號尚未綁定家庭成員身分」，不洩漏家庭資料，FR-015），已綁定則呼叫 statistics-service `GET /api/statistics/monthly` 並回覆該月依支付帳戶彙總結果（FR-014）於 `notification-service/src/main/java/com/family195home/notification/controller/LineWebhookController.java`（依賴 T059）
- [ ] T064 [US6] 擴充 LINE Webhook 邏輯：非綁定碼、非 `YYYY-MM` 格式訊息一律回覆格式提示訊息，不視為有效查詢（FR-014）於 `LineWebhookController.java`（依賴 T063）
- [ ] T065 [P] [US6] 單元測試：`YYYY-MM` 格式解析正確性、未綁定帳號查詢阻擋（FR-015）、無效格式回覆提示於 `notification-service/src/test/java/com/family195home/notification/LineWebhookControllerTest.java`

**Checkpoint**: 所有 User Story（US1-US6）應皆可獨立運作

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 跨 Story 的收尾工作與部署完整性

- [ ] T066 [P] 實作 `GET /api/notifications/logs?familyGroupId=&month=` 通知發送紀錄查詢端點（維運/測試用途）於 `notification-service/src/main/java/com/family195home/notification/controller/NotificationLogController.java`
- [ ] T067 [P] 完善 `docker-compose.yml`：加入各服務健康檢查（Spring Boot Actuator）與 `depends_on` 條件式啟動順序、注入 config-repo 對應環境變數
- [ ] T068 [P] 建立 `.github/workflows/cd.yml`：build 各服務映像檔 → 推送 GHCR → SSH 部署至可存取 VM 執行 `docker compose pull && up -d`（見 research.md 決策 1）
- [ ] T069 [P] 撰寫 `README.md`：系統架構圖、各服務職責說明、技術選型理由（含「為什麼不用 .NET」「為什麼不用訊息佇列」之具體回答，依 constitution 開發流程規範）
- [ ] T070 依 [quickstart.md](./quickstart.md) 逐項執行 US1-US6 驗證場景，確認端對端可正常運作
- [ ] T071 [P] 前端關鍵元件測試（Vitest + React Testing Library）：登入表單、支出新增表單驗證邏輯於 `frontend/tests/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：無相依，可立即開始
- **Foundational (Phase 2)**：依賴 Setup 完成，並阻擋所有 User Story 開發
- **User Stories (Phase 3-8)**：皆依賴 Foundational 完成後才可開始
  - US1、US2（P1）可平行開發（不同服務：family-service vs expense-service）
  - US3、US4（P2）依賴 US2 的 ExpenseRecord/PaymentAccount 端點已存在（T038、T034），故需在 US2 完成後開始
  - US5、US6（P3）依賴 US1（FamilyMember/authorize）與 US4（statistics-service）已完成；US6 直接建立於 US5 的 Webhook 控制器之上（T063 依賴 T059），為序列相依
- **Polish (Phase 9)**：依賴所有欲交付的 User Story 完成

### User Story Dependencies

- **US1 (P1)**：Foundational 完成後即可開始，無其他 Story 相依
- **US2 (P1)**：Foundational 完成後即可開始，無其他 Story 相依（可與 US1 平行）
- **US3 (P2)**：需 US2 的 `POST /api/expenses`、`POST /api/accounts` 端點已存在才有資料可篩選
- **US4 (P2)**：需 US2 的 ExpenseRecord 資料存在，statistics-service 呼叫 expense-service `GET /api/expenses`
- **US5 (P3)**：需 US1 的 FamilyMember、US4 的 statistics-service 已完成
- **US6 (P3)**：需 US5 的 LINE Webhook 控制器（LineWebhookController.java）已建立，於同檔案上擴充查詢分支

### Within Each User Story

- Entity/repository → Service 層邏輯 → Controller 端點 → 前端頁面/API 封裝
- 單元測試可與對應 Service 實作平行開發，但需在該 Service 邏輯完成後才能通過

### Parallel Opportunities

- Setup 階段所有標 [P] 任務可平行執行（T002-T009、T011）
- Foundational 階段 T012/T013、T015、T017、T018/T019 可平行執行（不同服務/檔案）
- Foundational 完成後，US1（family-service + 前端）與 US2（expense-service + 前端）可由不同人平行開發
- 各 Story 內標 [P] 的 entity/測試/前端頁面任務可平行執行

---

## Parallel Example: User Story 1

```bash
# 平行建立 entity：
Task: "建立 FamilyGroup entity 與 repository 於 family-service/.../domain/FamilyGroup.java"
Task: "建立 FamilyMember entity 與 repository 於 family-service/.../domain/FamilyMember.java"

# Service/Controller 邏輯完成後，平行進行：
Task: "單元測試：群組名稱唯一性、單一在職群組限制等於 FamilyServiceTest.java"
Task: "前端家庭群組頁面於 frontend/src/pages/FamilyGroupPage.tsx"
Task: "前端成員列表頁面於 frontend/src/pages/MembersPage.tsx"
```

---

## Implementation Strategy

### MVP First（User Story 1 + 2）

1. 完成 Phase 1：Setup
2. 完成 Phase 2：Foundational（阻擋所有 Story，含帳號註冊/登入與 JWT 機制）
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
8. Polish → 部署與文件收尾

### Parallel Team Strategy

多人協作時：

1. 團隊共同完成 Setup + Foundational
2. Foundational 完成後：
   - 開發者 A：US1（family-service + 前端家庭群組頁面）
   - 開發者 B：US2（expense-service + 前端記帳頁面）
3. US1、US2 皆完成後，US3、US4 可平行由不同開發者接手
4. US5、US6 因序列相依（同一 Webhook 控制器），建議由同一開發者接續完成

---

## Notes

- [P] 任務 = 不同檔案、無相依關係
- [Story] 標籤將任務對應至特定 User Story 以利追蹤
- 每個 User Story 應可獨立完成並測試
- 測試任務對應 constitution 原則 IV 明列之核心商業邏輯，非樣板程式碼不強制測試
- 每完成一項任務或一個邏輯群組後建議提交（commit）
- 可於任一 Checkpoint 停下並獨立驗證該 Story
- 避免：模糊任務描述、同檔案衝突、破壞 Story 獨立性的跨 Story 相依
</content>
