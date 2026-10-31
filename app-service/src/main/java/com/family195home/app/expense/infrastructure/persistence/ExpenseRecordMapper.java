package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.domain.ExpenseRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpenseRecordMapper {

    void insert(ExpenseRecord record);

    /** 僅當鎖仍由 lockHolderMemberId 持有時才更新，並於同一個 UPDATE 釋放鎖；回傳影響列數。 */
    int updateIfLockHeld(
            @Param("record") ExpenseRecord record,
            @Param("lockHolderMemberId") Long lockHolderMemberId);

    /** 僅當鎖仍由 lockHolderMemberId 持有時才刪除；回傳影響列數。 */
    int deleteByIdIfLockHeld(
            @Param("id") Long id, @Param("lockHolderMemberId") Long lockHolderMemberId);

    ExpenseRecord selectById(@Param("id") Long id);

    List<ExpenseRecord> selectByFilter(
            @Param("familyGroupId") Long familyGroupId,
            @Param("paymentAccountId") Long paymentAccountId,
            @Param("authorMemberId") Long authorMemberId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive);

    List<ExpenseRecord> selectPageByFilter(
            @Param("familyGroupId") Long familyGroupId,
            @Param("paymentAccountId") Long paymentAccountId,
            @Param("authorMemberId") Long authorMemberId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByFilter(
            @Param("familyGroupId") Long familyGroupId,
            @Param("paymentAccountId") Long paymentAccountId,
            @Param("authorMemberId") Long authorMemberId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive);

    int tryLock(
            @Param("id") Long id,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now,
            @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore);

    void releaseLock(@Param("id") Long id);

    int countByPaymentAccountId(@Param("paymentAccountId") Long paymentAccountId);

    List<Long> selectUsedPaymentAccountIds(@Param("familyGroupId") Long familyGroupId);
}
