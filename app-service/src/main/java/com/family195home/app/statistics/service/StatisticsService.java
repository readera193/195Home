package com.family195home.app.statistics.service;

import com.family195home.app.expense.application.PaymentAccountRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.service.ExpenseService;
import com.family195home.app.statistics.dto.AccountSummaryView;
import com.family195home.app.statistics.dto.MonthlySummaryResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依支付帳戶彙總月結淨額（FR-011）。同進程直接呼叫 {@link ExpenseService}
 * 取得資料（見 research.md 決策 4）。
 */
@Service
public class StatisticsService {

    private final ExpenseService expenseService;
    private final PaymentAccountRepository paymentAccountRepository;

    public StatisticsService(ExpenseService expenseService, PaymentAccountRepository paymentAccountRepository) {
        this.expenseService = expenseService;
        this.paymentAccountRepository = paymentAccountRepository;
    }

    public MonthlySummaryResponse summarize(Long familyGroupId, String month) {
        List<ExpenseRecord> records = expenseService.list(familyGroupId, null, null, month);

        Map<Long, Long> netByAccount = new LinkedHashMap<>();
        for (ExpenseRecord record : records) {
            netByAccount.merge(record.getPaymentAccountId(), record.getAmount().longValue(), Long::sum);
        }

        List<AccountSummaryView> accounts = netByAccount.entrySet().stream()
                .map(entry -> new AccountSummaryView(entry.getKey(), accountName(entry.getKey()), entry.getValue()))
                .toList();
        long total = accounts.stream().mapToLong(AccountSummaryView::netAmount).sum();

        // FR-011：當月無資料時回傳空陣列與 totalNetAmount: 0，而非錯誤或空白畫面
        return new MonthlySummaryResponse(familyGroupId, month, accounts, total);
    }

    private String accountName(Long paymentAccountId) {
        return paymentAccountRepository.findById(paymentAccountId).map(a -> a.getName()).orElse(null);
    }
}
