package com.family195home.app.demo;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo 體驗登入：建立隨機帳號後，一併建立家庭群組、支付帳戶與範例支出，讓使用者登入即有內容可看。
 * 放在獨立的 demo 套件，由它協調 family 與 expense 兩個模組，避免兩者互相依賴。
 */
@Service
public class DemoLoginService {

    private record SampleExpense(int accountIndex, int daysAgo, int amount, String note) {
    }

    private static final String[] ACCOUNT_NAMES = {"現金", "信用卡", "悠遊卡"};

    // 涵蓋本月與上月，統計頁才有資料可比較；支出為負數、收入（退款）為正數
    private static final SampleExpense[] SAMPLES = {
            new SampleExpense(0, 0, -85, "早餐"),
            new SampleExpense(2, 1, -30, "捷運"),
            new SampleExpense(1, 2, -1280, "超市採買"),
            new SampleExpense(0, 4, -220, "午餐"),
            new SampleExpense(1, 6, -3600, "水電瓦斯"),
            new SampleExpense(2, 8, -45, "公車"),
            new SampleExpense(1, 11, -899, "網路訂閱"),
            new SampleExpense(0, 14, -450, "家庭聚餐"),
            new SampleExpense(1, 18, 300, "商品退款"),
            new SampleExpense(0, 25, -160, "市場買菜"),
            new SampleExpense(1, 33, -2400, "衣物"),
            new SampleExpense(2, 38, -120, "交通儲值"),
            new SampleExpense(0, 42, -380, "晚餐"),
    };

    private final UserService userService;
    private final FamilyService familyService;
    private final PaymentAccountService paymentAccountService;
    private final ExpenseService expenseService;

    public DemoLoginService(
            UserService userService,
            FamilyService familyService,
            PaymentAccountService paymentAccountService,
            ExpenseService expenseService) {
        this.userService = userService;
        this.familyService = familyService;
        this.paymentAccountService = paymentAccountService;
        this.expenseService = expenseService;
    }

    @Transactional
    public UserService.LoginResult login() {
        UserService.LoginResult result = userService.loginAsDemo();
        Long userId = result.userId();

        // 群組名稱須全系統唯一，沿用 Demo 帳號的隨機後綴
        FamilyGroup group = familyService.createGroup(userId, result.displayName() + " 的家");
        FamilyMember member = familyService.getMyMembership(userId).orElseThrow();

        PaymentAccount[] accounts = new PaymentAccount[ACCOUNT_NAMES.length];
        for (int i = 0; i < ACCOUNT_NAMES.length; i++) {
            accounts[i] = paymentAccountService.create(group.getId(), userId, ACCOUNT_NAMES[i]);
        }

        LocalDateTime now = LocalDateTime.now();
        for (SampleExpense sample : SAMPLES) {
            expenseService.create(
                    group.getId(),
                    member.getId(),
                    accounts[sample.accountIndex()].getId(),
                    BigDecimal.valueOf(sample.amount()),
                    sample.note(),
                    randomTimeOn(now, sample.daysAgo()));
        }
        return result;
    }

    // 隨機時間：當天不得晚於現在（避免未來時間），其餘日子落在 07:00–22:59
    private static LocalDateTime randomTimeOn(LocalDateTime now, int daysAgo) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int secondOfDay = daysAgo == 0
                ? random.nextInt(Math.max(1, now.toLocalTime().toSecondOfDay()))
                : random.nextInt(7 * 3600, 23 * 3600);
        return now.toLocalDate().minusDays(daysAgo).atStartOfDay().plusSeconds(secondOfDay).withNano(0);
    }
}
