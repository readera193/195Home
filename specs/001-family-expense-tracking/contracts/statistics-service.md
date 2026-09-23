# Contract: statistics-service

無獨立資料庫；即時呼叫 expense-service 彙總後回傳。

### `GET /api/statistics/monthly?familyGroupId=&month=YYYY-MM`
- Header: `Authorization: Bearer <JWT>`（statistics-service 本地驗證簽章；並轉發該 JWT 呼叫 family-service 的 `authorize` 端點確認呼叫者屬於 `familyGroupId`，避免跨家庭資料外洩，FR-017）
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
- 邏輯：呼叫 expense-service `GET /api/expenses?familyGroupId=&month=` 取得該月全部紀錄，依 `paymentAccountId` 加總 `amount`；當月無紀錄時回傳空陣列與 `totalNetAmount: 0`（FR-011，非錯誤或空白畫面）
- `totalNetAmount` MUST 等於該月所有支出紀錄金額加總（SC-004）
- 供網頁統計頁面（月份選擇器切換任意過去月份）與 notification-service（月結通知、LINE 關鍵字查詢）共用；notification-service 呼叫時無平台使用者 JWT，改帶 `X-Internal-Token` Header，此情境 statistics-service 略過 `authorize` 呼叫（家庭歸屬已由 notification-service 依 LINE 綁定關係決定）
