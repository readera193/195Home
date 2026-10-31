# 0004 LINE 登入 + 自行簽發並本地驗證 JWT

- 狀態：採用
- 日期：2026-10-31（整理自 research.md 決策 7）

## 背景

使用者是家庭成員，已經有 LINE；不想讓使用者管理另一組密碼。系統只有一個對外 API 入口（app-service），另有一條服務間呼叫。

## 決定

- **無密碼**：以 LINE 授權碼換取 profile，LINE userId 即帳號；首次登入自動建立使用者。另提供展示用的 Demo 登入（預設關閉，由環境變數啟用）。
- 登入成功後由 app-service 簽發 HS256 JWT（JJWT），由自己的 Spring Security filter 在本地驗證簽章與到期，不需獨立 Gateway。
- **角色與在職狀態不放進 JWT**：這些會變動，expense／statistics 需要時直接呼叫 family 模組確認，避免被移除的成員在 token 到期前仍持有舊權限。
- 服務間呼叫用 `X-Internal-Token`，LINE Webhook 用 `X-Line-Signature`，與使用者 JWT 分開。
- 登出只是前端丟棄 token，不做伺服器端黑名單。

## 影響

- 好處：使用者體驗簡單；驗證不需額外網路呼叫；權限即時反映成員狀態。
- 代價：每次需要角色判斷都會查一次 family 資料；token 無法被伺服器主動撤銷（有效期 2 小時）。
- 備註：兄弟專案 task-board-practice 改用 Spring Resource Server 的標準做法，兩者都是對各自需求合適的選擇。
