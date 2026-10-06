package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.domain.ExpenseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExpenseRecordMapper {

    void insert(ExpenseRecord record);

    void update(ExpenseRecord record);

    void deleteById(@Param("id") Long id);

    ExpenseRecord selectById(@Param("id") Long id);

    List<ExpenseRecord> selectByFilter(
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
}
