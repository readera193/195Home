# Contract: gateway-service 路由表

Spring Cloud Gateway 為前端與外部系統（LINE 平台）的統一入口，依路徑前綴靜態路由至各服務（docker-compose 網路服務名稱，見 research.md 決策 6）。

| 路徑前綴 | 目標服務 | 說明 |
|----------|----------|------|
| `/api/users/**` | family-service | 帳號註冊/登入/登出 |
| `/api/families/**` | family-service | 家庭群組、成員、LINE 綁定碼 |
| `/api/line-bindings/**` | family-service | 內部服務對服務呼叫，不對前端開放（Gateway 層阻擋外部直接呼叫） |
| `/api/accounts/**` | expense-service | 支付帳戶 |
| `/api/expenses/**` | expense-service | 支出紀錄與鎖定 |
| `/api/statistics/**` | statistics-service | 月結彙總統計 |
| `/api/line/webhook` | notification-service | LINE 平台 Webhook（不需 `Authorization`，改用 `X-Line-Signature` 驗證） |
| `/api/notifications/**` | notification-service | 通知發送紀錄查詢 |

**存取控制**：Gateway 對 `/api/**`（除 `/api/users/register`、`/api/users/login`、`/api/line/webhook` 外）驗證 `Authorization: Bearer <JWT>` 的簽章與過期時間——使用與各服務共用的密鑰**本地驗證**，不需呼叫 family-service，驗證失敗回傳 `401`；驗證通過後原樣轉發該 JWT 給下游服務，供服務需要判斷「當下角色/在職狀態」時呼叫 family-service 的 `authorize` 端點使用（見 [family-service.md](./family-service.md)、research.md 決策 7）。前端一律透過 gateway 呼叫，不得繞過閘道分散呼叫各服務（符合技術範疇邊界要求）。

**服務間內部呼叫**：不經過 gateway、直接以 docker-compose 服務名稱互相呼叫的情境（例如 notification-service 排程呼叫 family-service、statistics-service 呼叫 expense-service），因無平台使用者 JWT 可用，一律改用共用密鑰 Header `X-Internal-Token` 驗證呼叫來源合法性。
