package com.family195home.app.expense.application;

import com.family195home.app.expense.domain.ExpenseRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRecordRepository {

    ExpenseRecord save(ExpenseRecord record);

    void update(ExpenseRecord record);

    void delete(Long id);

    Optional<ExpenseRecord> findById(Long id);

    /**
     * 依家庭範圍、可選的支付帳戶/新增者/月份篩選（FR-008/FR-009/FR-010）。
     * month 格式為 "YYYY-MM"，為 null 時不限定月份。
     */
    List<ExpenseRecord> findByFilter(Long familyGroupId, Long paymentAccountId, Long authorMemberId, String month);

    /** 同 {@link #findByFilter}，加上分頁（依 occurred_at、id 由新到舊）；offset 為略過筆數。 */
    List<ExpenseRecord> findPageByFilter(
            Long familyGroupId, Long paymentAccountId, Long authorMemberId, String month, int offset, int limit);

    /** 符合篩選條件的總筆數，供分頁計算總頁數。 */
    long countByFilter(Long familyGroupId, Long paymentAccountId, Long authorMemberId, String month);

    /**
     * 條件式 UPDATE 取得鎖：僅當目前未鎖定或鎖已逾時才會成功，回傳影響列數（見 research.md 決策 3）。
     */
    int tryLock(Long id, Long memberId, LocalDateTime now, long ttlMinutes);

    void releaseLock(Long id);

    /** 該支付帳戶是否已被任何支出紀錄使用（FR-022）。 */
    boolean existsByPaymentAccountId(Long paymentAccountId);
}
