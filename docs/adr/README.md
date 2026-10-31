# 架構決策紀錄（ADR）

記錄「為什麼這樣做」的關鍵決策。完整的技術選型過程與細節見 [research.md](../../specs/001-family-expense-tracking/research.md)，這裡只保留決策、取捨與影響，方便快速閱讀。

| 編號 | 決策 | 狀態 |
|---|---|---|
| [0001](0001-mybatis-over-jpa.md) | 資料存取採 MyBatis，不用 JPA | 採用 |
| [0002](0002-two-services-sync-rest.md) | 兩個服務、同步 REST、單一 MySQL 兩個 schema | 採用 |
| [0003](0003-expense-edit-lock.md) | 支出編輯鎖以條件式 UPDATE 實作 | 採用 |
| [0004](0004-auth-line-login-jwt.md) | LINE 登入 + 自行簽發並本地驗證 JWT | 採用 |

格式：背景（Context）→ 決定（Decision）→ 影響（Consequences，含代價）。新增決策請沿用此格式並更新上表。
