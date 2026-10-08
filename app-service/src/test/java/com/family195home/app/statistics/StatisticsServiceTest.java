package com.family195home.app.statistics;

import com.family195home.app.expense.application.PaymentAccountRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.service.ExpenseService;
import com.family195home.app.statistics.domain.MonthlySummary;
import com.family195home.app.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    private ExpenseService expenseService;
    private PaymentAccountRepository paymentAccountRepository;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        expenseService = mock(ExpenseService.class);
        paymentAccountRepository = mock(PaymentAccountRepository.class);
        statisticsService = new StatisticsService(expenseService, paymentAccountRepository);
    }

    @Test
    void summarize_accountTotalsSumToOverallTotal() {
        // SC-004：各帳戶淨額加總須等於該月所有支出紀錄金額總和
        when(expenseService.list(10L, null, null, "2026-09")).thenReturn(List.of(
                record(1L, -350),
                record(1L, -200),
                record(2L, 30000),
                record(2L, -1500)));
        when(paymentAccountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "現金")));
        when(paymentAccountRepository.findById(2L)).thenReturn(Optional.of(account(2L, "銀行帳戶")));

        MonthlySummary result = statisticsService.summarize(10L, "2026-09");

        long recordSum = -350 - 200 + 30000 - 1500;
        assertThat(result.accounts().stream().mapToLong(a -> a.netAmount()).sum()).isEqualTo(recordSum);
        assertThat(result.totalNetAmount()).isEqualTo(recordSum);
    }

    @Test
    void summarize_returnsZeroTotalWhenNoRecordsThatMonth() {
        // FR-011：當月無資料時回傳空陣列與 totalNetAmount: 0，而非錯誤或空白畫面
        when(expenseService.list(10L, null, null, "2099-01")).thenReturn(List.of());

        MonthlySummary result = statisticsService.summarize(10L, "2099-01");

        assertThat(result.accounts()).isEmpty();
        assertThat(result.totalNetAmount()).isZero();
    }

    private ExpenseRecord record(Long paymentAccountId, int amount) {
        ExpenseRecord record = new ExpenseRecord();
        record.setPaymentAccountId(paymentAccountId);
        record.setAmount(amount);
        record.setOccurredAt(LocalDateTime.now());
        return record;
    }

    private PaymentAccount account(Long id, String name) {
        PaymentAccount account = new PaymentAccount();
        account.setId(id);
        account.setName(name);
        return account;
    }
}
