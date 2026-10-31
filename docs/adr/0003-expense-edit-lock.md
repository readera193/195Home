# 0003 支出編輯鎖以條件式 UPDATE 實作

- 狀態：採用（含已知限制）
- 日期：2026-10-31（整理自 research.md 決策 3）

## 背景

多位家人可能同時編輯同一筆支出，需要避免互相覆寫。不希望為此引入 Redis 等分散式鎖元件。

## 決定

在 `expense_records` 加上 `locked_by_member_id`、`locked_at`。取得鎖時執行單一條件式 UPDATE：

```sql
UPDATE expense_records SET locked_by_member_id = ?, locked_at = ?
WHERE id = ? AND (locked_by_member_id IS NULL OR locked_at < ?)  -- ? = 現在 - 5 分鐘
```

影響筆數為 1 才算取得鎖，同時搶鎖時有且只有一人成功（資料庫的列鎖保證）。鎖有 5 分鐘 TTL，使用者異常關閉頁面也不會永久鎖死。更新與刪除需持有自己的鎖。

## 影響

- 好處：只靠現有 MySQL 就能互斥；實作與測試都簡單。
- 已知限制：`update`／`delete` 目前是「先檢查鎖再寫入」兩步驟，鎖在兩步之間逾時並被他人取得的時間窗很小但存在。鎖逾時後的覆寫也無法完全防止。
- 可改進方向：把 update／delete 也改成 `UPDATE ... WHERE id = ? AND locked_by_member_id = ?`，以影響筆數判斷是否仍持有鎖，使「檢查」與「寫入」變成同一個原子操作。尚未實作。
