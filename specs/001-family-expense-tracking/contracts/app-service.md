# Contract: app-service

單一 Spring Boot 應用，內部依業務領域分為 family、expense、statistics 三個套件模組，資料庫：`appdb`。對外提供兩組介面：

1. **公開 API**：前端（同源，因前端靜態檔案由 app-service 直接 serve）呼叫，需 `Authorization: Bearer <JWT>`（除 `POST /api/auth/line`、`POST /api/auth/demo`、`GET /api/auth/config` 外）。
2. **內部 API**：僅供 notification-service 呼叫，因該情境無平台使用者 JWT（發話者是 LINE 帳號），一律以共用密鑰 Header `X-Internal-Token` 驗證呼叫來源（見 research.md 決策 7）。

app-service 內部 family/expense/statistics 模組間的呼叫（例如 expense 模組確認呼叫者角色、statistics 模組取得支出資料）皆為同進程 Java service 方法呼叫，**不透過 HTTP，因此不出現在本合約中**——本合約僅描述跨部署服務邊界（前端 ↔ app-service、notification-service ↔ app-service）的介面。

## 共通約定

### 錯誤回應格式（Problem Details）

所有公開 API 與內部 API 的錯誤（含 Spring Security 在 filter 層攔下的 401 / 403）一律以 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details 回應，`Content-Type: application/problem+json`：

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "您已屬於一個家庭群組",
  "instance": "/api/families",
  "code": "ALREADY_IN_A_GROUP"
}
```

| 欄位 | 說明 |
|------|------|
| `status` / `title` | HTTP 狀態碼與其標準說明 |
| `detail` | 給使用者看的錯誤訊息（前端直接顯示） |
| `code` | **穩定的機器可讀錯誤代碼**，即本合約各端點 `Errors` 所列的代碼；前端與 notification-service 以 `code` 判斷分支，不可依賴 `detail` 文字 |
| `instance` | 發生錯誤的請求路徑 |
| `errors`（僅 `VALIDATION_ERROR`） | 欄位驗證失敗清單：`[{ "field": "code", "message": "..." }]` |

實作策略：業務錯誤一律以 `ApiException(kind, code, message)` 丟出，由 `GlobalExceptionHandler` 統一轉成 Problem Details（`ErrorKind` 對應 HTTP 狀態碼）；**不使用 Result 型別**。Spring MVC 自身的例外（缺少參數、JSON 格式錯誤、路徑不存在等）同樣轉為 Problem Details，`code` 為其 HTTP 狀態名稱（如 `BAD_REQUEST`、`NOT_FOUND`）。

通用錯誤代碼（任何端點都可能出現）：

| HTTP | code | 說明 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | 請求本體欄位驗證失敗（`@Valid`），`errors[]` 列出各欄位 |
| 400 | `INVALID_DATE_FORMAT` | 日期時間（`occurredAt`）、日期（`startDate`／`endDate`）或月份（`month`）格式錯誤 |
| 401 | `UNAUTHORIZED` | 未帶 token、token 無效或已過期；內部 API 的 `X-Internal-Token` 缺少或錯誤亦同 |
| 403 | `FORBIDDEN` | 已登入但沒有權限（Spring Security 層級） |
| 500 | `INTERNAL_ERROR` | 未預期的伺服器錯誤；`detail` 為固定文字，不洩漏內部細節 |

業務層通用錯誤代碼：

| HTTP | code | 說明 |
|------|------|------|
| 403 | `NOT_GROUP_MEMBER` | 呼叫者不是該家庭群組的在職成員（FR-017） |
| 403 | `NOT_GROUP_ADMIN` | 該操作限群組管理者 |
| 403 | `NOT_SELF` | 該操作限成員本人（離開群組） |
| 404 | `GROUP_NOT_FOUND` / `ACCOUNT_NOT_FOUND` / `EXPENSE_NOT_FOUND` / `MEMBER_NOT_FOUND` | 資源不存在 |
| 409 | `MEMBER_NOT_ACTIVE` | 目標成員目前不是「正常」狀態（離開／移出時） |

### 請求追蹤（`X-Request-Id`）

每個請求都帶有 requestId：沿用呼叫端送來的 `X-Request-Id`（限英數字與 `._-`，最長 64 字元），沒有或格式不合法則自行產生 UUID。requestId 會寫入每行日誌，並一律於回應標頭 `X-Request-Id` 回傳，使用者回報問題時可憑此查日誌。notification-service 呼叫內部 API 時會轉送自己的 requestId，兩個服務的日誌因此可串接。

### API 文件

對外 API（不含 `/api/internal/**`）提供 OpenAPI 文件：Swagger UI 於 `/swagger-ui.html`，規格於 `/v3/api-docs`。

### 分頁

列表型端點若支援分頁（目前僅 `GET /api/expenses`），使用 query `page`（從 0 起算，預設 0）與 `size`（預設 20，上限 100），回應為：

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalElements": 45, "totalPages": 3 }
```

`page < 0`、`size < 1` 或 `size > 100` → `400 INVALID_PAGE_PARAMS`。排序固定為 `occurredAt` 由新到舊，`occurredAt` 相同時以 `id` 由大到小，確保翻頁結果穩定。

## 帳號與登入（公開 API）

### `POST /api/auth/line`
- Request: `{ code: string }`（LINE 授權碼）
- Response 200: `{ token: string (JWT), userId: number, displayName: string, expiresAt: string }`；首次登入自動建立帳號，之後登入同步 LINE 暱稱（FR-024；已自訂顯示名稱者不覆蓋，FR-031）
- Errors: `400`（`code` 為空）、`401 LINE_LOGIN_FAILED`（授權碼無效／過期或 LINE 服務異常）
- 說明：`token` 為 HS256 簽章 JWT，Claim 含 `sub`(userId)、`displayName`、`iat`、`exp`（2 小時後過期），由 app-service 自行簽發與驗證。

### `POST /api/auth/demo`
- Request: 無 body
- Response 200: 同 `POST /api/auth/line`；每次建立全新帳號（`lineUserId = "demo-<uuid>"`，`displayName = "使用者"`）
- Errors: `404 DEMO_LOGIN_DISABLED`（未設定 `DEMO_LOGIN_ENABLED=true`）、`429 DEMO_LOGIN_RATE_LIMITED`（同一來源 IP 每小時超過 `app.demo-login.max-per-client-per-hour`（預設 20）次，或全站每小時超過 `app.demo-login.max-total-per-hour`（預設 300）次；固定一小時時間窗、僅記憶體計數；來源 IP 依反向代理的 forwarded 標頭還原）
- 說明：登入同時建立範例家庭群組（`使用者的家 #<userId>`，帳號為管理者，另有爸爸、媽媽、小明、小美 4 位示範成員）、5 個支付帳戶（現金、中信、國泰、三信、富邦）、範例支出（上月 1 日到今天，每天隨機 0–5 筆，隨機分配給各成員）；供展示環境免 LINE 體驗；Demo 帳號不會收到 LINE 月結推播。
- 資料保留：啟用 Demo 的環境每小時清除一次建立超過 `app.demo-login.retention-hours`（預設 24）小時、且成員全為 Demo 帳號的群組（連同其支出紀錄、支付帳戶、成員紀錄；每次最多 50 個群組），以及已無任何群組成員身分的過期 Demo 帳號；有真實 LINE 使用者的群組不會被清除。各模組只刪除自己的資料表，由 demo 套件依「支出紀錄 → 支付帳戶 → 成員與群組 → 帳號」的順序協調。

### `GET /api/auth/config`
- Response 200: `{ demoLoginEnabled: boolean }`，前端據此決定是否顯示 Demo 體驗按鈕

> 無 `POST /api/users/logout` 端點：JWT 為無狀態設計，登出僅為前端捨棄 token 的客戶端行為，伺服器不做強制註銷（見 research.md 決策 7）。

## 家庭群組（公開 API）

### `GET /api/families/me`
- Header: `Authorization`
- Response 200（已加入群組）: `{ familyGroupId, name, status: "ACTIVE"|"DISSOLVED", role: "ADMIN"|"MEMBER", inviteCode, familyMemberId }`
- Response 200（未加入任何群組）: `{ familyGroupId: null, name: null, status: null, role: null, inviteCode: null, familyMemberId: null }`
- 說明：供前端取得目前使用者所屬家庭群組與角色

### `POST /api/families`
- Header: `Authorization`
- Request: `{ name: string }`
- Response 201: `{ familyGroupId, name, inviteCode, role: "ADMIN" }`
- Errors: `409 GROUP_NAME_TAKEN`（FR-001）、`409 ALREADY_IN_A_GROUP`（FR-026）

### `POST /api/families/join`
- Header: `Authorization`
- Request: `{ inviteCode: string }`
- Response 200: `{ familyGroupId, name: null, role: "MEMBER" | "ADMIN", status: "ACTIVE" }`（`name` 目前恆為 `null`，前端加入後以 `GET /api/families/me` 取得群組名稱）
- Errors: `404 INVALID_INVITE_CODE`、`409 ALREADY_IN_A_GROUP`（FR-026）、`409 GROUP_DISSOLVED`、`403 MEMBER_REMOVED`（被管理者移出者不可以邀請碼重新加入，FR-030）

### `GET /api/families/{familyGroupId}/members`
- Header: `Authorization`
- Query: `includeLeft=true|false`（預設 true，供篩選清單使用，FR-021）
- Response 200: `[{ familyMemberId, userId, displayName, status: "ACTIVE"|"LEFT"|"REMOVED", role, joinedAt, leftAt }]`

### `POST /api/families/{familyGroupId}/members/{memberId}/leave`
- Header: `Authorization`（限本人）
- Request: `{ newAdminMemberId?: number }`（ADMIN 且尚有其他在職成員時必填）
- Response 200: `{ status: "LEFT", familyGroupStatus: "ACTIVE" | "DISSOLVED", newAdminMemberId: number | null }`
- Errors: `403 NOT_SELF`、`404 MEMBER_NOT_FOUND`、`409 MEMBER_NOT_ACTIVE`、`400 NEW_ADMIN_REQUIRED`、`400 CANNOT_TRANSFER_TO_SELF`
- 邏輯：若為唯一在職成員 → 群組 `DISSOLVED`（FR-018）；若為 ADMIN 且尚有其他在職成員 → 必須指定 `newAdminMemberId` 並轉移 ADMIN（FR-029）；離開者 `role` 重設為 `MEMBER`（FR-033）

### `POST /api/families/{familyGroupId}/transfer-admin`
- Header: `Authorization`（限該群組 ADMIN）
- Request: `{ newAdminMemberId: number }`
- Response 200: 新管理者的成員資料（同 members 單筆結構，`role: "ADMIN"`）
- Errors: `403 NOT_GROUP_ADMIN`、`400 CANNOT_TRANSFER_TO_SELF`、`409 MEMBER_NOT_ACTIVE`、`404 MEMBER_NOT_FOUND`
- 邏輯：原 ADMIN 降為 `MEMBER` 並留在群組，目標成員升為 ADMIN（FR-032）

### `POST /api/families/{familyGroupId}/members/{memberId}/kick`
- Header: `Authorization`（限該群組 ADMIN）
- Response 200: `{ memberId, status: "REMOVED" }`（被移出者不可以邀請碼重新加入，FR-028、FR-030）
- Errors: `403 NOT_GROUP_ADMIN`（FR-028）、`400 CANNOT_KICK_SELF`、`409 MEMBER_NOT_ACTIVE`

### `PUT /api/families/{familyGroupId}/members/{memberId}/display-name`
- Header: `Authorization`（呼叫者須為該群組在職成員；編輯自己不需 ADMIN，編輯他人限 ADMIN，FR-031）
- Request: `{ displayName: string }`（非空白、最長 50 字，前後空白會去除）
- Response 200: 同 `GET .../members` 的單筆成員 `{ familyMemberId, userId, displayName, status, role, joinedAt, leftAt }`
- Errors: `400`（欄位驗證失敗）、`403 NOT_GROUP_MEMBER`、`403 NOT_GROUP_ADMIN`（一般成員編輯他人）、`404 MEMBER_NOT_FOUND`
- 說明：顯示名稱存於 `users.display_name`，並將 `display_name_customized` 設為 true，之後 LINE 登入不再覆蓋。

### `POST /api/families/{familyGroupId}/members/{memberId}/restore-eligibility`
- Header: `Authorization`（限該群組 ADMIN）
- Response 200: `{ memberId, status: "LEFT" }`（`REMOVED` → `LEFT`，之後該成員可依 FR-025 以邀請碼重新加入，FR-030）
- Errors: `403 NOT_GROUP_ADMIN`、`404 MEMBER_NOT_FOUND`、`409 MEMBER_NOT_REMOVED`

## 支付帳戶（公開 API）

本節所有成功回應的帳戶結構皆為 `{ accountId, familyMemberId, name, status: "ACTIVE"|"DISABLED", deletable: boolean }`（以下簡稱「帳戶結構」）：`familyMemberId` 為建立者（管理者）的成員 id；`deletable` 表示該帳戶從未被任何支出紀錄使用、可真正刪除（FR-022），前端僅在 `deletable` 為 true 時顯示「刪除」。以 `accountId` 操作的端點，帳戶不存在時回 `404 ACCOUNT_NOT_FOUND`。

### `POST /api/accounts`
- Header: `Authorization`（限該群組 ADMIN，否則 `403 NOT_GROUP_ADMIN`，FR-003）
- Request: `{ familyGroupId, name }`（`familyMemberId` 由 JWT 解出的呼叫者身分決定，不由前端傳入）
- Response 201: 帳戶結構（`status: "ACTIVE"`、`deletable: true`，FR-003）
- Errors: `403 NOT_GROUP_MEMBER`、`403 NOT_GROUP_ADMIN`、`404 GROUP_NOT_FOUND`、`409 GROUP_DISSOLVED`（FR-018）

### `GET /api/accounts?familyGroupId=&status=ACTIVE|DISABLED|ALL`
- Header: `Authorization`（呼叫者須為該群組在職成員，否則 `403 NOT_GROUP_MEMBER`，FR-017）
- Response 200: 帳戶結構陣列，依建立時間排序；`status` 省略或為 `ALL` 時回傳所有狀態

### `PUT /api/accounts/{accountId}`
- Header: `Authorization`（限該群組 ADMIN）
- Request: `{ name }`
- Response 200: 帳戶結構
- Errors: `403 NOT_GROUP_ADMIN`

### `DELETE /api/accounts/{accountId}`
- Header: `Authorization`（限該群組 ADMIN）
- Response 204（僅限從未被支出紀錄使用的帳戶，FR-022）
- Errors: `403 NOT_GROUP_ADMIN`、`409 ACCOUNT_IN_USE`（已被使用，請改用停用）

### `POST /api/accounts/{accountId}/disable`
- Header: `Authorization`（限該群組 ADMIN，否則 `403 NOT_GROUP_ADMIN`）
- Response 200: 帳戶結構（`status: "DISABLED"`；FR-022，僅軟停用，不可真正刪除）

### `POST /api/accounts/{accountId}/enable`
- Header: `Authorization`（限該群組 ADMIN，否則 `403 NOT_GROUP_ADMIN`）
- Response 200: 帳戶結構（`status: "ACTIVE"`；FR-022，重新啟用已停用帳戶；已是 `ACTIVE` 時冪等回傳 200）
- Errors: `403 NOT_GROUP_ADMIN`、`409 GROUP_DISSOLVED`（群組已解散，FR-018）

## 支出紀錄（公開 API）

### `POST /api/expenses`
- Header: `Authorization`
- Request: `{ familyGroupId, paymentAccountId, amount: number, note: string, occurredAt?: string }`（`authorMemberId` 由 JWT 解出的呼叫者身分決定；`note` 必填、最長 500 字，超過為 `400 VALIDATION_ERROR`）
- Response 201: `{ expenseId, amount, note, occurredAt, paymentAccountId, paymentAccountName, authorMemberId, locked }`
- Errors: `400 AMOUNT_MUST_BE_INTEGER`、`400 AMOUNT_OUT_OF_RANGE`（超出 32 位元整數範圍）、`400 NOTE_REQUIRED`、`400 PAYMENT_ACCOUNT_REQUIRED`、`403 NOT_GROUP_MEMBER`、`403 ACCOUNT_NOT_IN_GROUP`（支付帳戶屬於其他群組）、`404 GROUP_NOT_FOUND`、`404 ACCOUNT_NOT_FOUND`、`409 ACCOUNT_DISABLED`（帳戶已停用，FR-022）、`409 GROUP_DISSOLVED`（FR-016、FR-018）
- 邏輯：`occurredAt` 未提供時使用伺服器當下時間（FR-005）

### `GET /api/expenses?familyGroupId=&paymentAccountId=&authorMemberId=&startDate=YYYY-MM-DD&endDate=YYYY-MM-DD&page=&size=`
- Header: `Authorization`（呼叫者須為該群組在職成員，否則 `403 NOT_GROUP_MEMBER`，FR-017）
- Response 200（分頁，見「共通約定 → 分頁」；`paymentAccountName` 於伺服器端每次請求僅查詢一次帳戶清單後對應，不隨筆數增加查詢次數）: `{ items: [{ expenseId, amount, note, occurredAt, paymentAccountId, paymentAccountName, authorMemberId, locked: boolean }], page, size, totalElements, totalPages }`
- 篩選條件可單獨或同時套用 `paymentAccountId`、`authorMemberId`（FR-008、FR-009、FR-010）；`startDate`／`endDate`（含頭含尾，各自可省略；省略則不限該側）可依日期區間篩選，前端預設帶入本月第一天與最後一天
- Errors: `400 INVALID_PAGE_PARAMS`、`400 INVALID_DATE_RANGE`（`startDate` 晚於 `endDate`）、`400 INVALID_DATE_FORMAT`（日期格式錯誤）
- 說明：statistics 模組彙總整月資料時走同進程的不分頁查詢，不受 `size` 上限影響（SC-004）

### `POST /api/expenses/{expenseId}/lock?familyGroupId=`
- Header: `Authorization`；Query `familyGroupId`（必填，用於驗證操作者屬於該群組）（操作者需為該群組在職成員，不限新增者本人，同進程呼叫 family 模組驗證，FR-019）
- Response 200: `{ expenseId, lockedByMemberId, lockedAt }`
- Errors: `409 RECORD_LOCKED`（其他人持有中，5 分鐘內，FR-027）

### `PUT /api/expenses/{expenseId}?familyGroupId=`
- Header: `Authorization`（須已持有鎖）；Query `familyGroupId`（必填，用於驗證操作者與紀錄皆屬於該群組）
- Request: `{ amount, note, occurredAt, paymentAccountId }`
- Response 200: `{ expenseId, ... }`（成功後自動釋放鎖）
- Errors: `403 LOCK_NOT_HELD_BY_CALLER`（未持有鎖，或鎖已逾時並被他人取得；檢查鎖與寫入為同一個條件式 UPDATE）、`403 NOT_GROUP_MEMBER`（非該群組在職成員，FR-017）、`403 EXPENSE_NOT_IN_GROUP`（紀錄不屬於該群組）、`404 EXPENSE_NOT_FOUND`、`400 AMOUNT_MUST_BE_INTEGER`／`400 AMOUNT_OUT_OF_RANGE`／`400 VALIDATION_ERROR` 等同新增驗證規則；更換為其他支付帳戶時另檢查 `ACCOUNT_NOT_FOUND`／`ACCOUNT_NOT_IN_GROUP`／`ACCOUNT_DISABLED`（沿用原帳戶時不檢查，已停用帳戶的歷史紀錄仍可編輯其他欄位）

### `DELETE /api/expenses/{expenseId}?familyGroupId=`
- Header: `Authorization`；Query `familyGroupId`（必填）（須已持有鎖；群組任一在職成員皆可鎖定後刪除，FR-019）
- Response 204
- Errors: `403 LOCK_NOT_HELD_BY_CALLER`、`403 NOT_GROUP_MEMBER`、`403 EXPENSE_NOT_IN_GROUP`

### `POST /api/expenses/{expenseId}/unlock`
- Header: `Authorization`（取消編輯時釋放鎖；呼叫者須為該紀錄所屬群組的在職成員，群組由紀錄本身推得，不需另帶 `familyGroupId`）
- Response 204（冪等：紀錄未被鎖定或鎖已逾時亦回 204）
- Errors: `404 EXPENSE_NOT_FOUND`、`403 NOT_GROUP_MEMBER`（FR-017）、`403 LOCK_NOT_HELD_BY_CALLER`（鎖仍有效且由其他成員持有，FR-027）

## 統計（公開 API）

### `GET /api/statistics/monthly?familyGroupId=&month=YYYY-MM`
- Header: `Authorization`（app-service 本地驗證簽章，並確認呼叫者屬於 `familyGroupId`，避免跨家庭資料外洩，FR-017）
- Response 200:
  ```json
  {
    "familyGroupId": 1,
    "month": "2026-09",
    "accounts": [
      { "paymentAccountId": 1, "paymentAccountName": "現金", "incomeAmount": 0, "expenseAmount": 1500, "netAmount": -1500 },
      { "paymentAccountId": 2, "paymentAccountName": "銀行帳戶", "incomeAmount": 30000, "expenseAmount": 0, "netAmount": 30000 }
    ],
    "totalIncomeAmount": 30000,
    "totalExpenseAmount": 1500,
    "totalNetAmount": 28500
  }
  ```
- 欄位：`incomeAmount` 為該帳戶當月正數金額加總、`expenseAmount` 為負數金額的絕對值加總（兩者皆為正數），`netAmount = incomeAmount − expenseAmount`；`total*` 為所有帳戶加總。`accounts[]` 只列出當月有紀錄的帳戶；帳戶已被刪除時 `paymentAccountName` 為 `null`（前端顯示「已刪除帳戶」）
- 邏輯：statistics 模組直接呼叫 expense 模組取得該月全部紀錄，依 `paymentAccountId` 分別加總收入與支出；當月無紀錄時回傳空陣列，且所有 `total*` 皆為 0（FR-011，非錯誤或空白畫面）
- `totalNetAmount` MUST 等於該月所有支出紀錄金額加總，且等於 `totalIncomeAmount − totalExpenseAmount`（SC-004）
- Errors: `403 NOT_GROUP_MEMBER`（FR-017）、`400 INVALID_DATE_FORMAT`（`month` 非 `YYYY-MM`）

## 內部 API（僅供 notification-service 呼叫，`X-Internal-Token` 驗證）

以下端點皆發生於 LINE Webhook 或排程情境，**無平台使用者 JWT**（發話者是 LINE 帳號而非已登入的平台 Session），一律以 Header `X-Internal-Token`（共用密鑰，見 research.md 決策 7）驗證呼叫來源（不使用 `Authorization: Bearer <JWT>`）：

### `GET /api/internal/line-bindings/by-line-user/{lineUserId}`
- Header: `X-Internal-Token`
- Response 200: `{ familyMemberId, familyGroupId }`
- 用途：LINE 關鍵字查詢時，依發話者的 LINE userId 找出其目前所屬家庭（路徑沿用 `line-bindings` 名稱，語意為「LINE 身分對應的在職成員」，無獨立綁定資料表）
- Errors: `404 NOT_BOUND`（該 LINE 帳號尚未登入過平台，或目前不屬於任何家庭群組）

### `GET /api/internal/line-bindings`
- Header: `X-Internal-Token`
- Response 200: `[{ familyMemberId, familyGroupId, lineUserId }]`
- 用途：每月排程通知（notification-service）取得所有家庭的在職成員及其 LINE userId

### `GET /api/internal/statistics/monthly?familyGroupId=&month=YYYY-MM`
- Header: `X-Internal-Token`
- Response：格式同公開版 `GET /api/statistics/monthly`（notification-service 只讀取各帳戶與全月的 `netAmount`／`totalNetAmount`，忽略其他欄位）
- 用途：notification-service 於每月排程通知與 LINE 關鍵字查詢時取得指定家庭的月結彙總；此情境已由 notification-service 依發話者的 LINE 身分決定家庭歸屬，故不再重複驗證呼叫者屬於該家庭
