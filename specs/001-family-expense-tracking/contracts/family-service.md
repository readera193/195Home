# Contract: family-service

負責使用者帳號、家庭群組、成員、邀請、LINE 綁定關係。資料庫：`familydb`。

## 驗證模型

- family-service 是唯一核發 JWT 的服務；其餘服務與 gateway-service 使用共用密鑰本地驗證 JWT 簽章與過期時間，不需回呼 family-service（見 research.md 決策 7、[gateway-routes.md](./gateway-routes.md)）。
- 家庭成員角色（ADMIN/MEMBER）與在職狀態不放入 JWT claim，需要即時判斷時呼叫本檔下方 `authorize` 端點。
- 無使用者情境的內部呼叫（如 notification-service 排程呼叫）改以 `X-Internal-Token` Header 驗證，詳見各自服務合約。

## 帳號與登入

### `POST /api/users/register`
- Request: `{ email: string, password: string }`
- Response 201: `{ userId: number, email: string }`
- Errors: `409 EMAIL_ALREADY_REGISTERED`（FR-024）

### `POST /api/users/login`
- Request: `{ email: string, password: string }`
- Response 200: `{ token: string (JWT), userId: number, email: string, expiresAt: string }`
- Errors: `401 INVALID_CREDENTIALS`
- 說明：`token` 為 HS256 簽章 JWT，Claim 含 `sub`(userId)、`email`、`iat`、`exp`（2 小時後過期）。

> 無 `POST /api/users/logout` 端點：JWT 為無狀態設計，登出僅為前端捨棄 token 的客戶端行為，伺服器不做強制註銷（見 research.md 決策 7）。

## 家庭群組

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

### `GET /api/families/{familyGroupId}/members/{memberId}/authorize?requiredRole=ADMIN|SELF_OR_ADMIN`
- 服務對服務呼叫，由 expense-service 於使用者請求脈絡下發起（**轉發使用者原始 `Authorization: Bearer <JWT>`**，非 `X-Internal-Token`；family-service 由 JWT 解出 `userId` 後查詢其於該家庭群組的當下角色/在職狀態，FR-019）
- Response 200: `{ authorized: boolean }`

## LINE 綁定

### `POST /api/families/members/{memberId}/line-binding-codes`
- Header: `Authorization`（限本人）
- Response 201: `{ code: string, expiresAt: string }`（10 分鐘有效，FR-023）

以下三個端點皆由 notification-service 在處理 LINE Webhook／排程時呼叫，該情境**無平台使用者 JWT**（發話者是 LINE 帳號而非已登入的平台 Session），一律改用 Header `X-Internal-Token`（共用密鑰，見 research.md 決策 7）驗證呼叫來源：

### `POST /api/line-bindings`
- Header: `X-Internal-Token`
- Request: `{ code: string, lineUserId: string }`
- Response 200: `{ familyMemberId, familyGroupId }`
- Errors: `410 CODE_EXPIRED_OR_USED`、`409 LINE_ACCOUNT_ALREADY_BOUND`（FR-020）

### `GET /api/line-bindings/by-line-user/{lineUserId}`
- Header: `X-Internal-Token`
- Response 200: `{ familyMemberId, familyGroupId }`
- Errors: `404 NOT_BOUND`

### `GET /api/line-bindings`
- Header: `X-Internal-Token`
- Response 200: `[{ familyMemberId, familyGroupId, lineUserId }]`
