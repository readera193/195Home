# 0003 支出編輯鎖以條件式 UPDATE 實作

- 狀態：採用
- 日期：2026-10-31（整理自 research.md 決策 3；update／delete 的原子化於同日補上）

## 背景

多位家人可能同時編輯同一筆支出，需要避免互相覆寫。不希望為此引入 Redis 等分散式鎖元件。

## 決定

在 `expense_records` 加上 `locked_by_member_id`、`locked_at`，所有「檢查鎖」都和「寫入」合成同一個條件式 SQL，讓資料庫的列鎖保證原子性：

```sql
-- 取得鎖：未鎖定或鎖已逾時（5 分鐘 TTL）才會成功
UPDATE expense_records SET locked_by_member_id = ?, locked_at = ?
WHERE id = ? AND (locked_by_member_id IS NULL OR locked_at < ?)

-- 更新：仍持有鎖才會寫入，並在同一個 UPDATE 釋放鎖
UPDATE expense_records SET ..., locked_by_member_id = NULL, locked_at = NULL
WHERE id = ? AND locked_by_member_id = ?

-- 刪除：仍持有鎖才會刪除
DELETE FROM expense_records WHERE id = ? AND locked_by_member_id = ?
```

影響筆數為 1 才算成功；0 代表鎖已不在呼叫者手上，回 403 `LOCK_NOT_HELD_BY_CALLER`。Service 仍會先做一次檢查，是為了在多數情況下給出明確的錯誤，真正的保證來自條件式 SQL。

## 影響

- 好處：只靠現有 MySQL 就能互斥；同時搶鎖有且只有一人成功；鎖在「檢查」與「寫入」之間逾時並被他人取得時，原持有者的寫入會落空而不是覆寫對方的內容。
- 代價：TTL 到期後鎖會被視為可搶，原持有者若在到期後才送出，只要鎖尚未被別人取得仍可成功；若已被取得則失敗，使用者需重新取得鎖再操作。
- 驗證：`ExpenseRecordMapperIntegrationTest` 在真實 MySQL 驗證「非持鎖者寫入／刪除影響 0 列、持鎖者更新同時釋放鎖」。
