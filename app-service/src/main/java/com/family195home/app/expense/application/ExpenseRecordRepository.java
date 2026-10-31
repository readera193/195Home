package com.family195home.app.expense.application;

import com.family195home.app.expense.domain.ExpenseRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExpenseRecordRepository {

    ExpenseRecord save(ExpenseRecord record);

    /** 僅當鎖仍由 lockHolderMemberId 持有時才更新並釋放鎖，回傳影響列數（0 表示鎖已不在該成員手上）。 */
    int updateIfLockHeld(ExpenseRecord record, Long lockHolderMemberId);

    /** 僅當鎖仍由 lockHolderMemberId 持有時才刪除，回傳影響列數。 */
    int deleteIfLockHeld(Long id, Long lockHolderMemberId);

    Optional<ExpenseRecord> findById(Long id);

    /** 依家庭範圍、可選的支付帳戶/新增者/月份篩選（FR-008/FR-009/FR-010）。 month 格式為 "YYYY-MM"，為 null 時不限定月份。 */
    List<ExpenseRecord> findByFilter(
            Long familyGroupId, Long paymentAccountId, Long authorMemberId, String month);

    /** 同 {@link #findByFilter}，改以起訖日期（含頭含尾，皆可為 null）篩選並分頁（依 occurred_at、id 由新到舊）； offset 為略過筆數。 */
    List<ExpenseRecord> findPageByFilter(
            Long familyGroupId,
            Long paymentAccountId,
            Long authorMemberId,
            LocalDate startDate,
            LocalDate endDate,
            int offset,
            int limit);

    /** 符合篩選條件的總筆數，供分頁計算總頁數。 */
    long countByFilter(
            Long familyGroupId,
            Long paymentAccountId,
            Long authorMemberId,
            LocalDate startDate,
            LocalDate endDate);

    /** 條件式 UPDATE 取得鎖：僅當目前未鎖定或鎖已逾時才會成功，回傳影響列數（見 research.md 決策 3）。 */
    int tryLock(Long id, Long memberId, LocalDateTime now, long ttlMinutes);

    /** 條件式 UPDATE 釋放鎖：僅當鎖由 callerMemberId 持有或已逾時才會清除，回傳影響列數。 */
    int releaseLockIfHeldOrExpired(
            Long id, Long callerMemberId, LocalDateTime now, long ttlMinutes);

    /** 該支付帳戶是否已被任何支出紀錄使用（FR-022）。 */
    boolean existsByPaymentAccountId(Long paymentAccountId);

    /** 該群組內已被任何支出紀錄使用的支付帳戶 id（FR-022，供判斷可否刪除）。 */
    Set<Long> findUsedPaymentAccountIds(Long familyGroupId);

    int deleteByFamilyGroupIds(List<Long> familyGroupIds);
}
