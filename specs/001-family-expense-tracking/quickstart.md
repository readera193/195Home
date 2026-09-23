# Quickstart: 家庭共享支出平台

本文件用於驗證核心功能是否端對端正確運作。詳細欄位與端點定義請參考 [data-model.md](./data-model.md) 與 [contracts/](./contracts/)。

## 前置需求

- Docker / Docker Compose
- （可選）LINE Developers 帳號與一組 Messaging API channel（測試 User Story 5、6 時需要；若僅驗證 User Story 1–4，可略過）

## 啟動系統

```bash
docker compose up -d --build
```

啟動後：
- 前端：`http://localhost:3000`
- API 入口（gateway-service）：`http://localhost:8080`
- 各服務健康檢查：`http://localhost:8080/api/<service>/actuator/health`

## 驗證場景（對應 spec.md 各 User Story 的 Independent Test）

### US1：建立家庭群組並邀請成員

1. 註冊帳號 A：`POST /api/users/register {email, password}` → 登入取得 token
2. 以帳號 A 建立群組：`POST /api/families {name: "王家"}` → 取得 `inviteCode`
3. 註冊帳號 B、登入
4. 以帳號 B 加入群組：`POST /api/families/join {inviteCode}`
5. **預期結果**：`GET /api/families/{id}/members` 回傳含 A（ADMIN）與 B（MEMBER）兩筆在職成員
6. 驗證唯一群組限制：帳號 A 再次呼叫 `POST /api/families` → 預期 `409 ALREADY_IN_A_GROUP`

### US2：建立支付帳戶並記錄支出

1. 以成員 B 建立支付帳戶：`POST /api/accounts {name: "現金"}`
2. 新增支出（未指定日期）：`POST /api/expenses {amount: -350, note: "午餐", paymentAccountId}`
3. **預期結果**：回傳紀錄的 `occurredAt` 為伺服器當下時間
4. 新增支出並指定金額為正數（收入）與 0：驗證皆儲存成功
5. 嘗試新增 `amount: 100.5`（小數）或缺少 `note` → 預期 `400`

### US3：查看與篩選支出紀錄

1. 以成員 A、B 各自新增數筆不同支付帳戶的支出
2. `GET /api/expenses?familyGroupId=` → 預期看到雙方所有紀錄
3. 加上 `paymentAccountId` 篩選 → 僅回傳該帳戶紀錄
4. 加上 `authorMemberId` 篩選 → 僅回傳該成員紀錄
5. 兩者同時套用 → 交集結果

### US4：查看基本統計

1. 呼叫 `GET /api/statistics/monthly?familyGroupId=&month=2026-09`
2. **預期結果**：`accounts[]` 各帳戶 `netAmount` 加總等於 `totalNetAmount`，且等於該月所有支出紀錄金額加總（可對照 US3 列表結果驗證，SC-004）
3. 切換至尚無資料的月份 → 預期 `totalNetAmount: 0`，非錯誤

### US5：每月自動 LINE 通知（需 LINE 測試帳號）

1. 成員登入後產生綁定碼：`POST /api/families/members/{id}/line-binding-codes`
2. 於 LINE Bot 對話輸入該綁定碼 → 預期收到綁定成功回覆
3. 手動觸發排程（測試環境可暫時調整 cron 或提供測試用觸發端點）
4. **預期結果**：已綁定成員的 LINE 收到當月支出匯總訊息；未綁定成員不受影響

### US6：LINE 關鍵字查詢月支出匯總

1. 已綁定成員於 LINE 傳送 `2026-07`
2. **預期結果**：收到 2026-07 依支付帳戶彙總的回覆內容
3. 傳送不符合格式的訊息（如 `hello`）→ 預期收到格式提示
4. 以尚未綁定的 LINE 帳號傳送 `2026-07` → 預期收到「尚未綁定」提示，不含任何家庭資料

## 執行測試

```bash
# 各後端服務（於各自服務目錄下）
./mvnw test

# 前端
cd frontend && npm test
```

## 停止與清除

```bash
docker compose down -v
```
