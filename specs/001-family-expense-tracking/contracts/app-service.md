# Contract: app-service

單一 Spring Boot 應用，內部依業務領域分為 family、expense、statistics 三個套件模組，資料庫：`appdb`。對外提供兩組介面：

1. **公開 API**：前端（同源，因前端靜態檔案由 app-service 直接 serve）呼叫，需 `Authorization: Bearer <JWT>`（除註冊/登入外）。
2. **內部 API**：僅供 notification-service 呼叫，因該情境無平台使用者 JWT（發話者是 LINE 帳號），一律以共用密鑰 Header `X-Internal-Token` 驗證呼叫來源（見 research.md 決策 7）。

app-service 內部 family/expense/statistics 模組間的呼叫（例如 expense 模組確認呼叫者角色、statistics 模組取得支出資料）皆為同進程 Java service 方法呼叫，**不透過 HTTP，因此不出現在本合約中**——本合約僅描述跨部署服務邊界（前端 ↔ app-service、notification-service ↔ app-service）的介面。

## 帳號與登入（公開 API）

### `POST /api/users/register`
- Request: `{ email: string, password: string }`
- Response 201: `{ userId: number, email: string }`
- Errors: `409 EMAIL_ALREADY_REGISTERED`（FR-024）

### `POST /api/users/login`
- Request: `{ email: string, password: string }`
- Response 200: `{ token: string (JWT), userId: number, email: string, expiresAt: string }`
- Errors: `401 INVALID_CREDENTIALS`
- 說明：`token` 為 HS256 簽章 JWT，Claim 含 `sub`(userId)、`email`、`iat`、`exp`（2 小時後過期），由 app-service 自行簽發與驗證。

> 無 `POST /api/users/logout` 端點：JWT 為無狀態設計，登出僅為前端捨棄 token 的客戶端行為，伺服器不做強制註銷（見 research.md 決策 7）。

## 家庭群組（公開 API）

### `POST /api/families`
- Header: `Authorization`
- Request: `{ name: string }`
- Response 201: `{ familyGroupId, name, inviteCode, role: "ADMIN" }`
- Errors: `409 GROUP_NAME_TAKEN`（FR-001）、`409 ALREADY_IN_A_GROUP`（FR-026）

### `POST /api/families/join`
- Header: `Authorization`
- Request: `{ inviteCode: string }`
- Response 200: `{ familyGroupId, name, role: "MEMBER" | "ADMIN", status: "ACTIVE" }`
- Errors: `404 INVALID_INVITE_CODE`、`409 ALREADY_IN_A_GROUP`（FR-026）、`409 GROUP_DISSOLVED`

### `GET /api/families/{familyGroupId}/members`
- Header: `Authorization`
- Query: `includeLeft=true|false`（預設 true，供篩選清單使用，FR-021）
- Response 200: `[{ familyMemberId, userId, email, status, role, joinedAt, leftAt }]`

### `POST /api/families/{familyGroupId}/members/{memberId}/leave`
- Header: `Authorization`（限本人）
- Response 200: `{ status: "LEFT", groupStatus: "ACTIVE" | "DISSOLVED", newAdminMemberId?: number }`
- 邏輯：若為唯一在職成員 → 群組 `DISSOLVED`（FR-018）；若為 ADMIN 且尚有其他在職成員 → 自動轉移 ADMIN（FR-029）

### `POST /api/families/{familyGroupId}/members/{memberId}/kick`
- Header: `Authorization`（限該群組 ADMIN）
- Response 200: `{ status: "LEFT" }`
- Errors: `403 NOT_GROUP_ADMIN`（FR-028）

### `POST /api/families/members/{memberId}/line-binding-codes`
- Header: `Authorization`（限本人）
- Response 201: `{ code: string, expiresAt: string }`（10 分鐘有效，FR-023）

## 支付帳戶（公開 API）

### `POST /api/accounts`
- Header: `Authorization`
- Request: `{ familyGroupId, name }`（`familyMemberId` 由 JWT 解出的呼叫者身分決定，不由前端傳入）
- Response 201: `{ accountId, name, status: "ACTIVE" }`（FR-003）

### `GET /api/accounts?familyGroupId=&status=ACTIVE|DISABLED|ALL`
- Header: `Authorization`
- Response 200: `[{ accountId, familyMemberId, name, status }]`

### `POST /api/accounts/{accountId}/disable`
- Header: `Authorization`
- Response 200: `{ accountId, status: "DISABLED" }`（FR-022，僅軟停用，不可真正刪除）

## 支出紀錄（公開 API）

### `POST /api/expenses`
- Header: `Authorization`
- Request: `{ familyGroupId, paymentAccountId, amount: number, note: string, occurredAt?: string }`（`authorMemberId` 由 JWT 解出的呼叫者身分決定）
- Response 201: `{ expenseId, amount, note, occurredAt, paymentAccountId, authorMemberId }`
- Errors: `400 AMOUNT_MUST_BE_INTEGER`、`400 NOTE_REQUIRED`、`400 PAYMENT_ACCOUNT_REQUIRED`、`409 GROUP_DISSOLVED`（FR-016、FR-018）
- 邏輯：`occurredAt` 未提供時使用伺服器當下時間（FR-005）

### `GET /api/expenses?familyGroupId=&paymentAccountId=&authorMemberId=&month=YYYY-MM`
- Header: `Authorization`
- Response 200: `[{ expenseId, amount, note, occurredAt, paymentAccountId, paymentAccountName, authorMemberId, locked: boolean }]`
- 篩選條件可單獨或同時套用 `paymentAccountId`、`authorMemberId`（FR-008、FR-009、FR-010）

### `POST /api/expenses/{expenseId}/lock`
- Header: `Authorization`（操作者需為本人或該群組 ADMIN，同進程呼叫 family 模組驗證，FR-019）
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

## 統計（公開 API）

### `GET /api/statistics/monthly?familyGroupId=&month=YYYY-MM`
- Header: `Authorization`（app-service 本地驗證簽章，並確認呼叫者屬於 `familyGroupId`，避免跨家庭資料外洩，FR-017）
- Response 200:
  ```json
  {
    "familyGroupId": 1,
    "month": "2026-09",
    "accounts": [
      { "paymentAccountId": 1, "paymentAccountName": "現金", "netAmount": -1500 },
      { "paymentAccountId": 2, "paymentAccountName": "銀行帳戶", "netAmount": 30000 }
    ],
    "totalNetAmount": 28500
  }
  ```
- 邏輯：statistics 模組直接呼叫 expense 模組取得該月全部紀錄，依 `paymentAccountId` 加總 `amount`；當月無紀錄時回傳空陣列與 `totalNetAmount: 0`（FR-011，非錯誤或空白畫面）
- `totalNetAmount` MUST 等於該月所有支出紀錄金額加總（SC-004）

## 內部 API（僅供 notification-service 呼叫，`X-Internal-Token` 驗證）

以下端點皆發生於 LINE Webhook 或排程情境，**無平台使用者 JWT**（發話者是 LINE 帳號而非已登入的平台 Session），一律以 Header `X-Internal-Token`（共用密鑰，見 research.md 決策 7）驗證呼叫來源，取代一般的 `Authorization: Bearer <JWT>`：

### `POST /api/internal/line-bindings`
- Header: `X-Internal-Token`
- Request: `{ code: string, lineUserId: string }`
- Response 200: `{ familyMemberId, familyGroupId }`
- Errors: `410 CODE_EXPIRED_OR_USED`、`409 LINE_ACCOUNT_ALREADY_BOUND`（FR-020）

### `GET /api/internal/line-bindings/by-line-user/{lineUserId}`
- Header: `X-Internal-Token`
- Response 200: `{ familyMemberId, familyGroupId }`
- Errors: `404 NOT_BOUND`

### `GET /api/internal/line-bindings`
- Header: `X-Internal-Token`
- Response 200: `[{ familyMemberId, familyGroupId, lineUserId }]`
- 用途：每月排程通知（notification-service）取得所有已綁定成員（跨所有家庭）

### `GET /api/internal/statistics/monthly?familyGroupId=&month=YYYY-MM`
- Header: `X-Internal-Token`
- Response：格式同公開版 `GET /api/statistics/monthly`
- 用途：notification-service 於每月排程通知與 LINE 關鍵字查詢時取得指定家庭的月結彙總；此情境已由 notification-service 依 LINE 綁定關係決定家庭歸屬，故不再重複驗證呼叫者屬於該家庭
