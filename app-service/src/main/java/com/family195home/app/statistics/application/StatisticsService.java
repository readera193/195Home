package com.family195home.app.statistics.application;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.statistics.domain.AccountSummary;
import com.family195home.app.statistics.domain.MonthlySummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 依支付帳戶彙總月結淨額（FR-011）。同進程直接呼叫 expense 模組的 {@link ExpenseService}、{@link PaymentAccountService}
 * 取得資料（見 research.md 決策 4），不直接使用其 repository（constitution 原則 I）。
 */
@Service
public class StatisticsService {

    private final ExpenseService expenseService;
    private final PaymentAccountService paymentAccountService;

    public StatisticsService(
            ExpenseService expenseService, PaymentAccountService paymentAccountService) {
        this.expenseService = expenseService;
        this.paymentAccountService = paymentAccountService;
    }

    public MonthlySummary summarize(Long familyGroupId, String month) {
        // 月份為空時 expense 模組會視為不限月份，須在此擋下，避免回傳整個歷史的彙總
        if (month == null || month.isBlank()) {
            throw new ApiException(ErrorKind.BAD_REQUEST, "INVALID_DATE_FORMAT", "日期時間或月份格式錯誤");
        }
        List<ExpenseRecord> records = expenseService.list(familyGroupId, null, null, month);
        Map<Long, String> accountNames = paymentAccountService.nameById(familyGroupId);

        // 金額為正是收入、為負是支出；每個帳戶分別累計 [收入, 支出（取絕對值）]
        Map<Long, long[]> totalsByAccount = new LinkedHashMap<>();
        for (ExpenseRecord record : records) {
            long amount = record.getAmount().longValue();
            long[] totals =
                    totalsByAccount.computeIfAbsent(record.getPaymentAccountId(), k -> new long[2]);
            if (amount >= 0) {
                totals[0] += amount;
            } else {
                totals[1] += -amount;
            }
        }

        List<AccountSummary> accounts =
                totalsByAccount.entrySet().stream()
                        .map(
                                entry ->
                                        new AccountSummary(
                                                entry.getKey(),
                                                accountNames.get(entry.getKey()),
                                                entry.getValue()[0],
                                                entry.getValue()[1],
                                                entry.getValue()[0] - entry.getValue()[1]))
                        .toList();
        long totalIncome = accounts.stream().mapToLong(AccountSummary::incomeAmount).sum();
        long totalExpense = accounts.stream().mapToLong(AccountSummary::expenseAmount).sum();

        // FR-011：當月無資料時回傳空陣列與全部為 0 的合計，而非錯誤或空白畫面
        return new MonthlySummary(
                familyGroupId,
                month,
                accounts,
                totalIncome,
                totalExpense,
                totalIncome - totalExpense);
    }
}
