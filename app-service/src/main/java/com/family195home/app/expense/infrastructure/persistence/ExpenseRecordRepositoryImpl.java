package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.application.ExpenseRecordRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import org.springframework.stereotype.Repository;

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
        LocalDateTime monthStart = null;
        LocalDateTime monthEndExclusive = null;
        if (month != null && !month.isBlank()) {
            YearMonth yearMonth = YearMonth.parse(month, YEAR_MONTH_FORMAT);
            monthStart = yearMonth.atDay(1).atStartOfDay();
            monthEndExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        }
        return mapper.selectByFilter(familyGroupId, paymentAccountId, authorMemberId, monthStart, monthEndExclusive);
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
