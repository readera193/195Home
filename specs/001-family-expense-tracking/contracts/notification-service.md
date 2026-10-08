# Contract: notification-service

負責 LINE Bot Webhook 接收、每月排程推播、關鍵字查詢、發送重試紀錄。資料庫：`notificationdb`。

## 驗證模型

本服務對 app-service 的所有呼叫皆發生於 LINE Webhook 或排程情境，**無平台使用者 JWT** 可用，一律以 Header `X-Internal-Token`（共用密鑰）驗證呼叫來源（不使用 `Authorization: Bearer <JWT>`，見 research.md 決策 7）。app-service 的呼叫目標位址（base URL）由環境變數設定：本機開發指向 docker-compose 服務名稱（例如 `http://app-service:8080`），Northflank 正式環境指向 app-service 對應 Northflank service 的內部網域。

## 與 app-service 的錯誤契約

app-service 的錯誤回應為 RFC 9457 Problem Details（見 `contracts/app-service.md` 共通約定）。`AppServiceClient` 只讀取 `code` 與 `detail` 兩個欄位（其餘欄位忽略），轉成 `AppServiceClientException(statusCode, errorCode, message)` 供上層依 `errorCode` 決定 LINE 回覆內容（目前使用 `CODE_EXPIRED_OR_USED`、`LINE_ACCOUNT_ALREADY_BOUND`；「未綁定」則依 404 狀態判斷）；回應本體不是 Problem Details（例如閘道 502 的 HTML）時，`errorCode` 為 `UNKNOWN`。

本服務自身的 HTTP 錯誤（缺少 query 參數、路徑不存在等）同樣以 Problem Details 回應（`spring.mvc.problemdetails.enabled=true`），與 app-service 格式一致。

## LINE Webhook（外部：LINE 平台呼叫）

### `POST /api/line/webhook`
- Header: `X-Line-Signature`（由 line-bot-sdk-java 驗證簽章）
- Request: LINE Messaging API 事件格式（text message event）
- 處理邏輯：
  1. 若訊息為 6 碼綁定碼格式 → 呼叫 app-service `POST /api/internal/line-bindings` 完成綁定，回覆綁定成功/失敗訊息（FR-023）
  2. 若訊息符合 `YYYY-MM` 格式 → 呼叫 app-service `GET /api/internal/line-bindings/by-line-user/{lineUserId}` 確認身分：
     - 未綁定 → 回覆「此帳號尚未綁定家庭成員身分」，不洩漏任何家庭資料（FR-015）
     - 已綁定 → 呼叫 app-service `GET /api/internal/statistics/monthly` 並回覆該月依支付帳戶彙總結果（FR-014）
  3. 其他格式訊息 → 回覆格式提示訊息，不視為有效查詢（FR-014）
- Response 200（LINE 平台要求立即回應，實際回覆訊息透過 LINE Reply/Push API 非同步送出）

## 每月排程通知（內部排程觸發，非對外 API）

- `@Scheduled(cron = "0 0 23 L * ?")`：每月最後一天 23:00 執行
- 邏輯：
  1. 呼叫 app-service `GET /api/internal/line-bindings` 取得所有已綁定成員（跨所有家庭）
  2. 若某家庭當下無任何已綁定 LINE 的成員 → 略過該家庭，不視為錯誤
  3. 對每位已綁定成員，呼叫 app-service `GET /api/internal/statistics/monthly` 取得其家庭當月（自月初至觸發當下）彙總，透過 LINE Push API 發送（FR-013）
  4. 發送失敗 → 自動重試最多 3 次；仍失敗 → 寫入 `NotificationLog(status=FAILED)` 並放棄，不影響其他成員（FR-013）
  5. 成功 → 寫入 `NotificationLog(status=SUCCESS)`

## 查詢紀錄（供維運/測試檢視，非核心功能）

### `GET /api/notifications/logs?familyGroupId=&month=YYYY-MM`
- Response 200: `[{ familyMemberId, lineUserId, status, attempts, lastAttemptAt, errorMessage }]`
