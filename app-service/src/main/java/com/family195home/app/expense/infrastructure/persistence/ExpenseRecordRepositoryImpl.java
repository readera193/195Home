package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.application.ExpenseRecordRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
public class ExpenseRecordRepositoryImpl implements ExpenseRecordRepository {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRecordMapper mapper;

    public ExpenseRecordRepositoryImpl(ExpenseRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExpenseRecord save(ExpenseRecord record) {
        mapper.insert(record);
        return record;
    }

    @Override
    public void update(ExpenseRecord record) {
        mapper.update(record);
    }

    @Override
    public boolean existsByPaymentAccountId(Long paymentAccountId) {
        return mapper.countByPaymentAccountId(paymentAccountId) > 0;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public Optional<ExpenseRecord> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<ExpenseRecord> findByFilter(Long familyGroupId, Long paymentAccountId, Long authorMemberId, String month) {
        MonthRange range = MonthRange.parse(month);
        return mapper.selectByFilter(familyGroupId, paymentAccountId, authorMemberId, range.start(), range.endExclusive());
    }

    @Override
    public List<ExpenseRecord> findPageByFilter(
            Long familyGroupId, Long paymentAccountId, Long authorMemberId,
            LocalDate startDate, LocalDate endDate, int offset, int limit) {
        return mapper.selectPageByFilter(
                familyGroupId, paymentAccountId, authorMemberId,
                startOf(startDate), endExclusiveOf(endDate), offset, limit);
    }

    @Override
    public long countByFilter(
            Long familyGroupId, Long paymentAccountId, Long authorMemberId, LocalDate startDate, LocalDate endDate) {
        return mapper.countByFilter(
                familyGroupId, paymentAccountId, authorMemberId, startOf(startDate), endExclusiveOf(endDate));
    }

    /** 起日當天 00:00；為 null 時不限下界。 */
    private static LocalDateTime startOf(LocalDate startDate) {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    /** 迄日隔天 00:00（不含）；為 null 時不限上界。 */
    private static LocalDateTime endExclusiveOf(LocalDate endDate) {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    /** "YYYY-MM" 轉成 [月初, 下月初) 區間；month 為空時不限定月份（兩端皆 null）。 */
    private record MonthRange(LocalDateTime start, LocalDateTime endExclusive) {
        static MonthRange parse(String month) {
            if (month == null || month.isBlank()) {
                return new MonthRange(null, null);
            }
            YearMonth yearMonth = YearMonth.parse(month, YEAR_MONTH_FORMAT);
            return new MonthRange(yearMonth.atDay(1).atStartOfDay(), yearMonth.plusMonths(1).atDay(1).atStartOfDay());
        }
    }

    @Override
    public int tryLock(Long id, Long memberId, LocalDateTime now, long ttlMinutes) {
        return mapper.tryLock(id, memberId, now, now.minusMinutes(ttlMinutes));
    }

    @Override
    public void releaseLock(Long id) {
        mapper.releaseLock(id);
    }
}
