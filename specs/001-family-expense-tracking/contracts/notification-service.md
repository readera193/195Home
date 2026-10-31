# Contract: notification-service

負責 LINE Bot Webhook 接收、每月排程推播、關鍵字查詢、發送重試紀錄。資料庫：`notificationdb`。

## 驗證模型

> 本服務對外有兩類端點：LINE Webhook（`X-Line-Signature`）與維運用的 `/api/notifications/**`（`X-Internal-Token`）；後者不應對公網開放，Northflank 上僅供內部網域存取，並仍以共用密鑰作第二層保護。

本服務對 app-service 的所有呼叫皆發生於 LINE Webhook 或排程情境，**無平台使用者 JWT** 可用，一律以 Header `X-Internal-Token`（共用密鑰）驗證呼叫來源（不使用 `Authorization: Bearer <JWT>`，見 research.md 決策 7）。app-service 的呼叫目標位址（base URL）由環境變數設定：本機開發指向 docker-compose 服務名稱（例如 `http://app-service:8080`），Northflank 正式環境指向 app-service 對應 Northflank service 的內部網域。

## 與 app-service 的錯誤契約

app-service 的錯誤回應為 RFC 9457 Problem Details（見 `contracts/app-service.md` 共通約定）。`AppServiceClient` 只讀取 `code` 與 `detail` 兩個欄位（其餘欄位忽略），轉成 `AppServiceClientException(statusCode, errorCode, message)` 供上層依 `errorCode` 決定 LINE 回覆內容（「LINE 帳號尚未加入任何家庭」依 404 狀態判斷）；回應本體不是 Problem Details（例如閘道 502 的 HTML）時，`errorCode` 為 `UNKNOWN`。

本服務自身的 HTTP 錯誤（缺少 query 參數、路徑不存在等）同樣以 Problem Details 回應（`spring.mvc.problemdetails.enabled=true`），與 app-service 格式一致。

## 請求追蹤

與 app-service 相同：每個請求沿用或產生 requestId（`X-Request-Id`，規則見 `contracts/app-service.md` 共通約定），寫入日誌並於回應標頭回傳；呼叫 app-service 時以 `X-Request-Id` 轉送目前的 requestId。

## LINE Webhook（外部：LINE 平台呼叫）

### `POST /api/line/webhook`
- Header: `X-Line-Signature`（由 line-bot-sdk-java 驗證簽章）
- Request: LINE Messaging API 事件格式（text message event）
- 處理邏輯：
  1. 若訊息符合 `YYYY-MM` 格式 → 呼叫 app-service `GET /api/internal/line-bindings/by-line-user/{lineUserId}` 確認發話者身分：
     - 找不到（404）→ 回覆「此帳號尚未加入任何家庭，請先至平台以 LINE 登入並建立或加入家庭。」，不洩漏任何家庭資料（FR-015）
     - 找到 → 呼叫 app-service `GET /api/internal/statistics/monthly` 並回覆該月依支付帳戶彙總結果（FR-014）
  2. 其他格式訊息 → 回覆格式提示訊息，不視為有效查詢（FR-014）
  3. Postback 事件（Rich Menu，FR-014）：依序決定查詢月份——
     - `params.date` 存在（「其他月份」的 Datetime Picker）→ 取其前 7 碼年月
     - `data = "month=current"`／`"month=previous"`（「本月」「上月」）→ 依 `Asia/Taipei` 時區換算為當月／上月
     - `data` 本身為 `YYYY-MM` → 直接使用
     - 以上皆不符 → 回覆格式提示

     決定月份後與文字訊息的 `YYYY-MM` 查詢走相同流程（含身分確認與不洩漏資料規則）
  4. 符合 `\d{4}-\d{2}` 但非合法年月（如 `2026-13`）→ 回覆格式提示；呼叫 app-service 失敗 → 回覆「目前無法查詢支出匯總，請稍後再試。」，不讓使用者收不到回應
  5. 其他事件（follow、unfollow、join 等）一律忽略

- 回覆內容格式（與每月推播共用）：第一行 `YYYY-MM 家庭支出匯總`，之後每個帳戶一行 `帳戶名稱：淨額`，最後一行 `總計：淨額`，金額加千分位；當月無紀錄時為 `本月尚無任何支出紀錄，淨額為 0`
- Rich Menu 須以 Messaging API 建立（Datetime Picker 無法於 LINE Official Account Manager 後台設定），見 `scripts/setup-line-richmenu.ps1`
- Response 200（LINE 平台要求立即回應，實際回覆訊息透過 LINE Reply/Push API 非同步送出）

## 每月排程通知（內部排程觸發，非對外 API）

- `@Scheduled(cron = "0 0 23 L * ?", zone = "Asia/Taipei")`：每月最後一天台灣時間 23:00 執行
- 邏輯：
  1. 呼叫 app-service `GET /api/internal/line-bindings` 取得所有家庭的在職成員及其 LINE userId（Demo 帳號已由 app-service 排除）
  2. 無在職成員的家庭不會出現在清單中，等同略過，不視為錯誤
  3. 對每位成員，呼叫 app-service `GET /api/internal/statistics/monthly` 取得其家庭當月（自月初至觸發當下）彙總，透過 LINE Push API 發送（FR-013）；同一家庭的彙總只查詢一次，供該家庭所有成員共用
  4. 發送失敗 → 最多嘗試 3 次（`app.notification.max-retries`），每次重試前等待 `app.notification.retry-backoff-ms`（預設 1000 毫秒）× 已嘗試次數；仍失敗 → 寫入 `NotificationLog(status=FAILED)` 並放棄，不影響其他成員（FR-013）。取得彙總失敗等其他例外同樣記為 `FAILED`（`attempts = 0`）並繼續處理下一位
  5. 成功 → 寫入 `NotificationLog(status=SUCCESS)`
  6. 每次執行產生一個 run id（`monthly-<YYYY-MM>-<8 碼>`）作為日誌的 requestId，並隨 `X-Request-Id` 轉送給 app-service

## 查詢紀錄（供維運/測試檢視，非核心功能）

### `GET /api/notifications/logs?familyGroupId=&month=YYYY-MM`
- Header: `X-Internal-Token`（與 app-service 相同的共用密鑰；缺少或錯誤 → `401 UNAUTHORIZED` Problem Details。LINE Webhook 不受此限，由 `X-Line-Signature` 驗證）
- Response 200: `[{ familyMemberId, lineUserId, status, attempts, lastAttemptAt, errorMessage }]`
