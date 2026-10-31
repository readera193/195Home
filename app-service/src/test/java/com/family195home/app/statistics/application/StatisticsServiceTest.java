package com.family195home.app.statistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.shared.ApiException;
import com.family195home.app.statistics.domain.MonthlySummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticsServiceTest {

    private ExpenseService expenseService;
    private PaymentAccountService paymentAccountService;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        expenseService = mock(ExpenseService.class);
        paymentAccountService = mock(PaymentAccountService.class);
        statisticsService = new StatisticsService(expenseService, paymentAccountService);
    }

    @Test
    void summarize_accountTotalsSumToOverallTotal() {
        // SC-004：各帳戶淨額加總須等於該月所有支出紀錄金額總和
        when(expenseService.list(10L, null, null, "2026-09"))
                .thenReturn(
                        List.of(
                                record(1L, -350),
                                record(1L, -200),
                                record(2L, 30000),
                                record(2L, -1500)));
        when(paymentAccountService.nameById(10L)).thenReturn(Map.of(1L, "現金", 2L, "銀行帳戶"));

        MonthlySummary result = statisticsService.summarize(10L, "2026-09");

        long recordSum = -350 - 200 + 30000 - 1500;
        assertThat(result.accounts().stream().mapToLong(a -> a.netAmount()).sum())
                .isEqualTo(recordSum);
        assertThat(result.totalNetAmount()).isEqualTo(recordSum);
    }

    @Test
    void summarize_splitsIncomeAndExpensePerAccount() {
        when(expenseService.list(10L, null, null, "2026-09"))
                .thenReturn(
                        List.of(
                                record(1L, -350),
                                record(1L, -200),
                                record(2L, 30000),
                                record(2L, -1500)));
        when(paymentAccountService.nameById(10L)).thenReturn(Map.of(1L, "現金", 2L, "銀行帳戶"));

        MonthlySummary result = statisticsService.summarize(10L, "2026-09");

        var cash = result.accounts().get(0);
        assertThat(cash.paymentAccountName()).isEqualTo("現金");
        assertThat(cash.incomeAmount()).isZero();
        assertThat(cash.expenseAmount()).isEqualTo(550);
        assertThat(cash.netAmount()).isEqualTo(-550);
        var bank = result.accounts().get(1);
        assertThat(bank.paymentAccountName()).isEqualTo("銀行帳戶");
        assertThat(bank.incomeAmount()).isEqualTo(30000);
        assertThat(bank.expenseAmount()).isEqualTo(1500);
        assertThat(bank.netAmount()).isEqualTo(28500);
        assertThat(result.totalIncomeAmount()).isEqualTo(30000);
        assertThat(result.totalExpenseAmount()).isEqualTo(2050);
        assertThat(result.totalNetAmount()).isEqualTo(27950);
    }

    @Test
    void summarize_returnsZeroTotalWhenNoRecordsThatMonth() {
        // FR-011：當月無資料時回傳空陣列與 totalNetAmount: 0，而非錯誤或空白畫面
        when(expenseService.list(10L, null, null, "2099-01")).thenReturn(List.of());

        MonthlySummary result = statisticsService.summarize(10L, "2099-01");

        assertThat(result.accounts()).isEmpty();
        assertThat(result.totalNetAmount()).isZero();
    }

    @Test
    void summarize_rejectsBlankMonthInsteadOfSummarizingAllHistory() {
        // 空白月份不可被當成「不限月份」而回傳整個歷史的彙總（contracts：400 INVALID_DATE_FORMAT）
        assertThatThrownBy(() -> statisticsService.summarize(10L, " "))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_DATE_FORMAT");
        verify(expenseService, never()).list(any(), any(), any(), any());
    }

    private ExpenseRecord record(Long paymentAccountId, int amount) {
        ExpenseRecord record = new ExpenseRecord();
        record.setPaymentAccountId(paymentAccountId);
        record.setAmount(amount);
        record.setOccurredAt(LocalDateTime.now());
        return record;
    }
}
