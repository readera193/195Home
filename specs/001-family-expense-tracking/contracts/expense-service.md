# Contract: expense-service

負責支付帳戶、支出紀錄（含併發編輯鎖定）。資料庫：`expensedb`。所有端點皆需 `Authorization: Bearer <JWT>` Header（由 gateway 轉發，expense-service 以共用密鑰本地驗證簽章取得呼叫者 `userId`）；涉及角色判斷或群組歸屬驗證時（例如編輯他人紀錄需 ADMIN、確認呼叫者屬於指定 `familyGroupId`），額外轉發該 JWT 呼叫 family-service 既有的 `GET /api/families/{id}/members/{id}/authorize` 端點確認當下角色與在職狀態（FR-017、FR-019）。

## 支付帳戶

### `POST /api/accounts`
- Request: `{ familyGroupId, familyMemberId, name }`
- Response 201: `{ accountId, name, status: "ACTIVE" }`（FR-003）

### `GET /api/accounts?familyGroupId=&status=ACTIVE|DISABLED|ALL`
- Response 200: `[{ accountId, familyMemberId, name, status }]`

### `POST /api/accounts/{accountId}/disable`
- Response 200: `{ accountId, status: "DISABLED" }`（FR-022，僅軟停用，不可真正刪除）

## 支出紀錄

### `POST /api/expenses`
- Request: `{ familyGroupId, authorMemberId, paymentAccountId, amount: number, note: string, occurredAt?: string }`
- Response 201: `{ expenseId, amount, note, occurredAt, paymentAccountId, authorMemberId }`
- Errors: `400 AMOUNT_MUST_BE_INTEGER`、`400 NOTE_REQUIRED`、`400 PAYMENT_ACCOUNT_REQUIRED`、`409 GROUP_DISSOLVED`（FR-016、FR-018）
- 邏輯：`occurredAt` 未提供時使用伺服器當下時間（FR-005）

### `GET /api/expenses?familyGroupId=&paymentAccountId=&authorMemberId=&month=YYYY-MM`
- Response 200: `[{ expenseId, amount, note, occurredAt, paymentAccountId, paymentAccountName, authorMemberId, locked: boolean }]`
- 篩選條件可單獨或同時套用 `paymentAccountId`、`authorMemberId`（FR-008、FR-009、FR-010）；`month` 供 statistics-service 彙總查詢使用

### `POST /api/expenses/{expenseId}/lock`
- Header: `Authorization`（操作者需為本人或該群組 ADMIN，呼叫 family-service 驗證，FR-019）
- Response 200: `{ expenseId, lockedByMemberId, lockedAt }`
- Errors: `409 RECORD_LOCKED`（其他人持有中，5 分鐘內，FR-027）

### `PUT /api/expenses/{expenseId}`
- Header: `Authorization`（須已持有鎖）
- Request: `{ amount, note, occurredAt, paymentAccountId }`
- Response 200: `{ expenseId, ... }`（成功後自動釋放鎖）
- Errors: `403 LOCK_NOT_HELD_BY_CALLER`、`400 AMOUNT_MUST_BE_INTEGER` 等同新增驗證規則

### `DELETE /api/expenses/{expenseId}`
- Header: `Authorization`（須已持有鎖，或本人/ADMIN 直接鎖定後刪除）
- Response 204
- Errors: `403 LOCK_NOT_HELD_BY_CALLER`

### `POST /api/expenses/{expenseId}/unlock`
- Header: `Authorization`（取消編輯時釋放鎖）
- Response 204
