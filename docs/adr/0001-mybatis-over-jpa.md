# 0001 資料存取採 MyBatis，不用 JPA

- 狀態：採用
- 日期：2026-10-31（整理自 research.md 決策 10）

## 背景

專案的查詢多為「依家庭、月份、支付帳戶篩選並彙總」與「條件式 UPDATE 取得編輯鎖」，需要清楚掌控 SQL 語意；同時希望每一筆查詢的行為都能被直接解釋。

## 決定

使用 MyBatis + XML Mapper 撰寫 SQL，搭配 Flyway 管理 schema。資料存取採 port/adapter 分層：domain 層定義 repository 介面，infrastructure 層的 `RepositoryImpl` 委派給 MyBatis `@Mapper`。Mapper XML 依模組（family、expense）分子目錄。

## 影響

- 好處：SQL 顯式可讀，沒有延遲載入與 N+1 等隱性行為；編輯鎖這類需要精確 `UPDATE ... WHERE` 條件的邏輯可以直接表達。
- 代價：沒有 JPA 的 dirty checking 與關聯映射，欄位與 Mapper 需手動維護；重複的 CRUD 樣板較多。
- 備註：兄弟專案 task-board-practice 刻意使用 JPA，用來對照兩種做法，兩者都是有意選擇。
