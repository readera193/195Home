# Phase 1 Data Model: 家庭共享支出平台

依 constitution 原則 I（微服務邊界與資料自主權），各實體依所屬服務分組，服務間不共用資料表；跨服務資料存取一律透過 `contracts/` 定義的 REST API。

## family-service（資料庫：`familydb`）

### User（使用者帳號）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| email | VARCHAR(255) UNIQUE NOT NULL | 全系統唯一（FR-024） |
| passwordHash | VARCHAR(255) NOT NULL | BCrypt 雜湊 |
| createdAt | DATETIME2 NOT NULL | |

**驗證規則**：註冊時 email 已存在 → 拒絕並提示「此 Email 已被註冊，請改用登入」（FR-024）。

### FamilyGroup（家庭群組）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(100) UNIQUE NOT NULL | 全系統唯一（FR-001） |
| status | VARCHAR(20) NOT NULL | `ACTIVE` \| `DISSOLVED` |
| inviteCode | VARCHAR(32) UNIQUE NOT NULL | 可重複使用、無時限（FR-002） |
| createdAt | DATETIME2 NOT NULL | |

**狀態轉換**：`ACTIVE` → `DISSOLVED`（唯一在職成員離開時，FR-018）。`DISSOLVED` 群組 MUST NOT 允許新增支付帳戶或支出紀錄。

### FamilyMember（成員身分）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyGroupId | BIGINT FK → FamilyGroup | |
| userId | BIGINT FK → User | |
| status | VARCHAR(20) NOT NULL | `ACTIVE` \| `LEFT` |
| role | VARCHAR(20) NOT NULL | `ADMIN` \| `MEMBER` |
| joinedAt | DATETIME2 NOT NULL | |
| leftAt | DATETIME2 NULL | |

**驗證規則**：
- 同一 `userId` 在整個系統中同時只能有一筆 `status = ACTIVE` 的 FamilyMember（FR-026）。
- 建立群組時，建立者自動成為 `role = ADMIN, status = ACTIVE` 的成員（FR-001）。
- 群組同時僅可有一位 `role = ADMIN` 的在職成員。

**狀態轉換**：
- （不存在）→ `ACTIVE`：建立群組或以邀請碼加入（FR-001、FR-002）。
- `ACTIVE` → `LEFT`：自行離開或被管理者移出（FR-021、FR-028）。
- `LEFT` → `ACTIVE`：以原群組邀請碼重新加入，`role` 維持原值或預設 `MEMBER`（FR-025）。
- `role`：`ADMIN` 離開仍有其他在職成員的群組時，自動將 `ADMIN` 轉移給群組內 `joinedAt` 最早的其他在職成員（FR-029）。

### LineBindingCode（LINE 綁定碼）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyMemberId | BIGINT FK → FamilyMember | |
| code | VARCHAR(16) UNIQUE NOT NULL | |
| expiresAt | DATETIME2 NOT NULL | 產生後 10 分鐘（FR-023） |
| used | BIT NOT NULL DEFAULT 0 | |
| createdAt | DATETIME2 NOT NULL | |

**驗證規則**：`used = 1` 或 `expiresAt < now` 的綁定碼不可再使用，須重新產生（FR-023）。

### LineBinding（LINE 帳號綁定關係）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyMemberId | BIGINT FK → FamilyMember UNIQUE | 每個成員身分僅一個綁定 |
| lineUserId | VARCHAR(64) UNIQUE NOT NULL | 每個 LINE 帳號僅能綁定一個成員身分（FR-020） |
| boundAt | DATETIME2 NOT NULL | |

**登入驗證**：採 JWT（見 research.md 決策 7），不儲存 Session 資料表；token 本身即攜帶 `userId`、`email`、簽發/過期時間，由 family-service 簽發，其餘服務與 gateway 以共用密鑰本地驗證簽章，無需查詢資料庫。

---

## expense-service（資料庫：`expensedb`）

### PaymentAccount（支付帳戶）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyMemberId | BIGINT NOT NULL | 參照 family-service 的成員 id（跨服務參照，非 FK） |
| familyGroupId | BIGINT NOT NULL | 供家庭範圍查詢與資料隔離（FR-017） |
| name | VARCHAR(100) NOT NULL | 例如「現金」「銀行帳戶」「信用卡」（FR-003） |
| status | VARCHAR(20) NOT NULL | `ACTIVE` \| `DISABLED` |
| createdAt | DATETIME2 NOT NULL | |

**驗證規則**：`DISABLED` 帳戶不可供新增支出紀錄選用，但既有支出紀錄仍完整顯示原帳戶名稱；已有支出紀錄關聯的帳戶 MUST NOT 真正刪除，僅能軟停用（FR-022）。

**狀態轉換**：`ACTIVE` → `DISABLED`（不可逆，僅軟停用）。

### ExpenseRecord（支出紀錄）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyGroupId | BIGINT NOT NULL | 資料隔離範圍（FR-017） |
| paymentAccountId | BIGINT FK → PaymentAccount NOT NULL | |
| authorMemberId | BIGINT NOT NULL | 參照 family-service 成員 id，新增者身分（FR-004） |
| amount | INT NOT NULL | 整數，可正可負可零；正數=流入，負數=流出（FR-016） |
| note | NVARCHAR(500) NOT NULL | 必填自由文字（FR-004） |
| occurredAt | DATETIME2 NOT NULL | 未指定時預設當下（FR-005）；可手動指定（FR-006） |
| createdAt | DATETIME2 NOT NULL | |
| updatedAt | DATETIME2 NOT NULL | |
| lockedByMemberId | BIGINT NULL | 併發編輯鎖定（FR-027），見 research.md 決策 3 |
| lockedAt | DATETIME2 NULL | |

**驗證規則**：
- `amount` 必須為整數（不接受小數點）；可正可負可零（FR-016）。
- `note`、`paymentAccountId` 為必填欄位，缺少則拒絕儲存（FR-016）。
- 編輯/刪除權限：本人新增者 或 該群組 `ADMIN` 成員 才可操作（FR-019）；其餘成員禁止（後端須呼叫 family-service 驗證身分與角色）。
- 編輯/刪除前須先成功取得 `lockedByMemberId` 鎖定，否則回傳「紀錄目前正被編輯中」錯誤（FR-027）。

---

## statistics-service（無獨立資料庫）

### MonthlyAccountSummary（月結帳戶彙總，僅為回應 DTO，不持久化）

| 欄位 | 型別 | 說明 |
|------|------|------|
| familyGroupId | BIGINT | |
| yearMonth | VARCHAR(7) | 格式 `YYYY-MM` |
| paymentAccountId | BIGINT | |
| paymentAccountName | VARCHAR(100) | 供顯示用（含已停用帳戶名稱） |
| netAmount | BIGINT | 該帳戶當月淨額（收入－支出加總） |

彙總來源：即時呼叫 expense-service `GET /api/expenses?familyGroupId=&month=` 取得該月所有紀錄後於記憶體彙總（見 research.md 決策 4）。各帳戶淨額加總 MUST 等於該月所有支出紀錄金額總和（SC-004）。

---

## notification-service（資料庫：`notificationdb`）

### NotificationLog（月結通知發送紀錄）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyGroupId | BIGINT NOT NULL | |
| familyMemberId | BIGINT NOT NULL | |
| lineUserId | VARCHAR(64) NOT NULL | |
| yearMonth | VARCHAR(7) NOT NULL | 格式 `YYYY-MM` |
| status | VARCHAR(20) NOT NULL | `SUCCESS` \| `FAILED` |
| attempts | INT NOT NULL DEFAULT 0 | 最多重試 3 次（FR-013） |
| lastAttemptAt | DATETIME2 NOT NULL | |
| errorMessage | NVARCHAR(500) NULL | |

**驗證規則**：單一成員單一月份發送失敗達重試上限（3 次）後標記 `FAILED` 並記錄錯誤，不影響其他成員發送（FR-013）。

---

## 跨服務參照關係總覽

```text
User (family-service)
 └─< FamilyMember >── FamilyGroup (family-service)
         │                  │
         ├─ LineBinding      │
         │                  │
         ▼                  ▼
   PaymentAccount ──< ExpenseRecord   (expense-service，以 familyMemberId / familyGroupId 數值參照，非 DB 層 FK)
                             │
                             ▼
                MonthlyAccountSummary  (statistics-service，即時運算，不持久化)
                             │
                             ▼
                   NotificationLog     (notification-service)
```

跨服務參照（`familyMemberId`、`familyGroupId`）僅為數值型別欄位，不建立跨資料庫 FK；資料一致性與存在性驗證由呼叫方服務於呼叫 REST API 時確認（例如 expense-service 新增支出前呼叫 family-service 確認 `familyMemberId` 屬於有效在職成員）。
