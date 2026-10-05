# Phase 1 Data Model: 家庭共享支出平台

依 constitution 原則 I（服務邊界與資料自主權），各實體依所屬**部署服務**分組；app-service 內再依**業務模組**（family、expense、statistics）細分，模組間不共用資料表，模組間如需彼此資料一律呼叫對方模組的 service 層方法（同進程 Java 方法呼叫，非 REST）。app-service 與 notification-service 之間唯一的跨服務資料存取透過 `contracts/app-service.md` 定義的內部 REST API。

## app-service（資料庫：`appdb`）

schema 定義對應 `app-service/src/main/resources/db/migration/`（Flyway migration script）。

### family 模組

#### User（使用者帳號）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| email | VARCHAR(255) UNIQUE NOT NULL | 全系統唯一（FR-024） |
| passwordHash | VARCHAR(255) NOT NULL | BCrypt 雜湊 |
| createdAt | DATETIME(6) NOT NULL | |

**驗證規則**：註冊時 email 已存在 → 拒絕並提示「此 Email 已被註冊，請改用登入」（FR-024）。

#### FamilyGroup（家庭群組）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(100) UNIQUE NOT NULL | 全系統唯一（FR-001） |
| status | VARCHAR(20) NOT NULL | `ACTIVE` \| `DISSOLVED` |
| inviteCode | VARCHAR(32) UNIQUE NOT NULL | 可重複使用、無時限（FR-002） |
| createdAt | DATETIME(6) NOT NULL | |

**狀態轉換**：`ACTIVE` → `DISSOLVED`（唯一在職成員離開時，FR-018）。`DISSOLVED` 群組 MUST NOT 允許新增支付帳戶或支出紀錄。

#### FamilyMember（成員身分）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyGroupId | BIGINT FK → FamilyGroup | |
| userId | BIGINT FK → User | |
| status | VARCHAR(20) NOT NULL | `ACTIVE` \| `LEFT` |
| role | VARCHAR(20) NOT NULL | `ADMIN` \| `MEMBER` |
| joinedAt | DATETIME(6) NOT NULL | |
| leftAt | DATETIME(6) NULL | |

**驗證規則**：
- 同一 `userId` 在整個系統中同時只能有一筆 `status = ACTIVE` 的 FamilyMember（FR-026）。
- 建立群組時，建立者自動成為 `role = ADMIN, status = ACTIVE` 的成員（FR-001）。
- 群組同時僅可有一位 `role = ADMIN` 的在職成員。

**狀態轉換**：
- （不存在）→ `ACTIVE`：建立群組或以邀請碼加入（FR-001、FR-002）。
- `ACTIVE` → `LEFT`：自行離開或被管理者移出（FR-021、FR-028）。
- `LEFT` → `ACTIVE`：以原群組邀請碼重新加入，`role` 維持原值或預設 `MEMBER`（FR-025）。
- `role`：`ADMIN` 離開仍有其他在職成員的群組時，自動將 `ADMIN` 轉移給群組內 `joinedAt` 最早的其他在職成員（FR-029）。

#### LineBindingCode（LINE 綁定碼）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyMemberId | BIGINT FK → FamilyMember | |
| code | VARCHAR(16) UNIQUE NOT NULL | |
| expiresAt | DATETIME(6) NOT NULL | 產生後 10 分鐘（FR-023） |
| used | TINYINT(1) NOT NULL DEFAULT 0 | |
| createdAt | DATETIME(6) NOT NULL | |

**驗證規則**：`used = 1` 或 `expiresAt < now` 的綁定碼不可再使用，須重新產生（FR-023）。

#### LineBinding（LINE 帳號綁定關係）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyMemberId | BIGINT FK → FamilyMember UNIQUE | 每個成員身分僅一個綁定 |
| lineUserId | VARCHAR(64) UNIQUE NOT NULL | 每個 LINE 帳號僅能綁定一個成員身分（FR-020） |
| boundAt | DATETIME(6) NOT NULL | |

**登入驗證**：採 JWT（見 research.md 決策 7），不儲存 Session 資料表；token 本身即攜帶 `userId`、`email`、簽發/過期時間，由 app-service 簽發與本地驗證，無需查詢資料庫。

---

### expense 模組

#### PaymentAccount（支付帳戶）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyMemberId | BIGINT NOT NULL | 參照 family 模組的成員 id（模組間參照，非 FK） |
| familyGroupId | BIGINT NOT NULL | 供家庭範圍查詢與資料隔離（FR-017） |
| name | VARCHAR(100) NOT NULL | 例如「現金」「銀行帳戶」「信用卡」（FR-003） |
| status | VARCHAR(20) NOT NULL | `ACTIVE` \| `DISABLED` |
| createdAt | DATETIME(6) NOT NULL | |

**驗證規則**：`DISABLED` 帳戶不可供新增支出紀錄選用，但既有支出紀錄仍完整顯示原帳戶名稱；已有支出紀錄關聯的帳戶 MUST NOT 真正刪除，僅能軟停用（FR-022）。

**狀態轉換**：`ACTIVE` → `DISABLED`（不可逆，僅軟停用）。

#### ExpenseRecord（支出紀錄）

| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | |
| familyGroupId | BIGINT NOT NULL | 資料隔離範圍（FR-017） |
| paymentAccountId | BIGINT FK → PaymentAccount NOT NULL | |
| authorMemberId | BIGINT NOT NULL | 參照 family 模組成員 id，新增者身分（FR-004） |
| amount | INT NOT NULL | 整數，可正可負可零；正數=流入，負數=流出（FR-016） |
| note | VARCHAR(500) NOT NULL | 必填自由文字，資料庫／欄位 charset 採 `utf8mb4`（FR-004） |
| occurredAt | DATETIME(6) NOT NULL | 未指定時預設當下（FR-005）；可手動指定（FR-006） |
| createdAt | DATETIME(6) NOT NULL | |
| updatedAt | DATETIME(6) NOT NULL | |
| lockedByMemberId | BIGINT NULL | 併發編輯鎖定（FR-027），見 research.md 決策 3 |
| lockedAt | DATETIME(6) NULL | |

**驗證規則**：
- `amount` 必須為整數（不接受小數點）；可正可負可零（FR-016）。
- `note`、`paymentAccountId` 為必填欄位，缺少則拒絕儲存（FR-016）。
- 編輯/刪除權限：本人新增者 或 該群組 `ADMIN` 成員 才可操作（FR-019）；其餘成員禁止（expense 模組的 service 層直接呼叫 family 模組的 service 方法確認身分與角色，同進程呼叫，見 research.md 決策 7）。
- 編輯/刪除前須先成功取得 `lockedByMemberId` 鎖定，否則回傳「紀錄目前正被編輯中」錯誤（FR-027）。

---

### statistics 模組（無獨立資料表）

#### MonthlyAccountSummary（月結帳戶彙總，僅為回應 DTO，不持久化）

| 欄位 | 型別 | 說明 |
|------|------|------|
| familyGroupId | BIGINT | |
| yearMonth | VARCHAR(7) | 格式 `YYYY-MM` |
| paymentAccountId | BIGINT | |
| paymentAccountName | VARCHAR(100) | 供顯示用（含已停用帳戶名稱） |
| netAmount | BIGINT | 該帳戶當月淨額（收入－支出加總） |

彙總來源：statistics 模組直接呼叫 expense 模組的 service 方法取得該月所有紀錄後於記憶體彙總（同進程方法呼叫，見 research.md 決策 4）。各帳戶淨額加總 MUST 等於該月所有支出紀錄金額總和（SC-004）。

---

## notification-service（資料庫：`notificationdb`）

schema 定義對應 `notification-service/src/main/resources/db/migration/`（Flyway migration script）。

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
| lastAttemptAt | DATETIME(6) NOT NULL | |
| errorMessage | VARCHAR(500) NULL | 資料庫／欄位 charset 採 `utf8mb4` |

**驗證規則**：單一成員單一月份發送失敗達重試上限（3 次）後標記 `FAILED` 並記錄錯誤，不影響其他成員發送（FR-013）。

---

## 模組/服務參照關係總覽

```text
┌─────────────────────────── app-service（單一進程） ───────────────────────────┐
│                                                                              │
│   User ──< FamilyMember >── FamilyGroup        (family 模組)                │
│              │                                                              │
│              ├─ LineBinding / LineBindingCode                              │
│              │                                                              │
│              ▼ （同進程方法呼叫，確認角色/在職狀態）                         │
│   PaymentAccount ──< ExpenseRecord             (expense 模組)               │
│              │                                                              │
│              ▼ （同進程方法呼叫，取得資料彙總）                             │
│      MonthlyAccountSummary                     (statistics 模組，不持久化)  │
│                                                                              │
└──────────────────────────────────┬───────────────────────────────────────┘
                                    │ 唯一跨進程呼叫：REST + X-Internal-Token
                                    ▼
                         NotificationLog          (notification-service)
```

模組間參照（`familyMemberId`、`familyGroupId`）僅為數值型別欄位，不建立跨模組資料表 FK；資料一致性與存在性驗證由呼叫方模組的 service 層於呼叫對方模組 service 方法時確認（例如 expense 模組新增支出前呼叫 family 模組確認 `familyMemberId` 屬於有效在職成員）。notification-service 對 app-service 的參照則透過 `contracts/app-service.md` 定義的內部 REST API 取得，同樣不建立跨資料庫 FK，一致性由 app-service 於回應時保證。
