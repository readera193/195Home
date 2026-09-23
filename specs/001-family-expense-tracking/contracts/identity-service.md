# Contract: identity-service

Base path（經 Gateway）：`/api/identity`

## 公開端點（前端使用，經 Gateway 轉發，需 JWT，除註冊/登入外）

### POST /auth/register
建立平台帳號（FR-024）。
- Request: `{ "email": string, "password": string, "displayName": string }`
- Response 201: `{ "userId": string }`
- Errors: 409 email 已存在

### POST /auth/login
- Request: `{ "email": string, "password": string }`
- Response 200: `{ "accessToken": string, "expiresIn": number }`
- Errors: 401 帳密錯誤

### POST /families
建立家庭群組（FR-001）。需登入。
- Request: `{ "name": string }`
- Response 201: `{ "familyGroupId": string, "name": string, "inviteCode": string }`
- Errors: 409 名稱重複；409 使用者已屬於某家庭群組（FR-026）

### POST /families/join
以邀請碼加入（FR-002）。
- Request: `{ "inviteCode": string }`
- Response 200: `{ "familyGroupId": string, "memberId": string, "status": "ACTIVE" }`
- Errors: 404 邀請碼無效；409 使用者已屬於某家庭群組（FR-026）

### GET /families/me
取得目前使用者所屬家庭群組與成員資訊。
- Response 200: `{ "familyGroupId": string, "name": string, "status": "ACTIVE"|"DISSOLVED", "role": "ADMIN"|"MEMBER", "inviteCode": string }`
- Response 200（未加入任何群組）: `{ "familyGroupId": null }`

### GET /families/{familyGroupId}/members
成員清單（含已離開，標示狀態，FR-021）。
- Response 200: `[{ "memberId": string, "userId": string, "displayName": string, "role": "ADMIN"|"MEMBER", "status": "ACTIVE"|"LEFT" }]`

### POST /families/{familyGroupId}/leave
成員自行離開（唯一成員離開時群組轉為 DISSOLVED，FR-018）。
- Response 200: `{ "status": "LEFT", "familyGroupStatus": "ACTIVE"|"DISSOLVED" }`

### POST /families/{familyGroupId}/members/{memberId}/remove
管理者強制移出成員（FR-028）。僅 ADMIN 可呼叫。
- Response 200: `{ "memberId": string, "status": "LEFT" }`
- Errors: 403 非管理者

### POST /line-binding-codes
成員登入平台後產生綁定碼（FR-023）。
- Response 201: `{ "code": string, "expiresAt": string(ISO8601) }`

## 內部端點（僅供其他微服務呼叫，不經公開路由，或以 internal header/network 限制）

### GET /internal/members/{userId}
供 `expense-service`/`notification-service` 查詢使用者目前家庭與角色。
- Response 200: `{ "memberId": string, "familyGroupId": string, "familyGroupStatus": "ACTIVE"|"DISSOLVED", "role": "ADMIN"|"MEMBER", "status": "ACTIVE"|"LEFT" }`
- Response 200（未加入任何群組）: `{ "memberId": null }`

### GET /internal/families/{familyGroupId}
供其他服務驗證家庭群組是否存在、是否 DISSOLVED。
- Response 200: `{ "familyGroupId": string, "status": "ACTIVE"|"DISSOLVED" }`

### POST /internal/line-binding-codes/{code}/consume
供 `notification-service` 消費綁定碼，完成 LINE 帳號綁定（FR-020、FR-023）。
- Request: `{ "lineUserId": string }`
- Response 200: `{ "memberId": string, "familyGroupId": string }`
- Errors: 410 綁定碼已過期或已使用；409 該 LINE 帳號已綁定其他成員身分（FR-020）

### GET /internal/line-identities/{lineUserId}
供 `notification-service` 查詢某 LINE 帳號對應的成員身分（用於關鍵字查詢與訊息路由）。
- Response 200: `{ "memberId": string, "familyGroupId": string }`
- Response 404: 該 LINE 帳號尚未綁定
