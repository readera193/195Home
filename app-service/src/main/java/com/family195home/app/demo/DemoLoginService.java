package com.family195home.app.demo;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyMember;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo 體驗登入：建立隨機帳號後，一併建立家庭群組、示範成員、支付帳戶與範例支出，讓使用者登入即有內容可看。 放在獨立的 demo 套件，由它協調 family 與 expense
 * 兩個模組，避免兩者互相依賴。
 */
@Service
public class DemoLoginService {

    private record SampleExpense(int accountIndex, int amount, String note) {}

    private static final String[] ACCOUNT_NAMES = {"現金", "中信", "國泰", "三信", "富邦"};

    private static final int MAX_PER_DAY = 5;

    // 登入者為管理者，另加入這幾位示範成員，範例支出會隨機分配給所有成員
    private static final String[] MEMBER_NAMES = {"爸爸", "媽媽", "小明", "小美"};

    // 範例支出樣板；支出為負數、收入（退款、獎金）為正數。
    // 涵蓋上月與本月，統計頁才有資料可比較
    private static final SampleExpense[] SAMPLES = {
        new SampleExpense(0, -85, "早餐"),
        new SampleExpense(2, -30, "捷運"),
        new SampleExpense(1, -165, "咖啡"),
        new SampleExpense(1, -1280, "超市採買"),
        new SampleExpense(3, -520, "加油"),
        new SampleExpense(0, -220, "午餐"),
        new SampleExpense(4, -350, "電影票"),
        new SampleExpense(1, -3600, "水電瓦斯"),
        new SampleExpense(0, -75, "手搖飲"),
        new SampleExpense(2, -45, "公車"),
        new SampleExpense(3, -680, "藥局"),
        new SampleExpense(1, -899, "網路訂閱"),
        new SampleExpense(4, -1500, "孩子才藝費"),
        new SampleExpense(0, -450, "家庭聚餐"),
        new SampleExpense(2, -60, "停車費"),
        new SampleExpense(3, 5000, "年終獎金"),
        new SampleExpense(1, 300, "商品退款"),
        new SampleExpense(4, -2100, "家電維修"),
        new SampleExpense(0, -95, "宵夜"),
        new SampleExpense(0, -160, "市場買菜"),
        new SampleExpense(2, -240, "計程車"),
        new SampleExpense(3, -1180, "寵物飼料"),
        new SampleExpense(1, -3200, "電信費"),
        new SampleExpense(1, -2400, "衣物"),
        new SampleExpense(4, -780, "書籍"),
        new SampleExpense(2, -120, "交通儲值"),
        new SampleExpense(0, -310, "早午餐"),
        new SampleExpense(3, -1450, "牙醫掛號"),
        new SampleExpense(0, -380, "晚餐"),
        new SampleExpense(4, -960, "日用品"),
        new SampleExpense(1, -540, "洗車"),
        new SampleExpense(0, -130, "水果"),
        new SampleExpense(2, -88, "便利商店"),
        new SampleExpense(3, -1999, "運動鞋"),
        new SampleExpense(4, -260, "下午茶"),
    };

    private final UserService userService;
    private final FamilyService familyService;
    private final PaymentAccountService paymentAccountService;
    private final ExpenseService expenseService;
    private final Clock clock;

    public DemoLoginService(
            UserService userService,
            FamilyService familyService,
            PaymentAccountService paymentAccountService,
            ExpenseService expenseService,
            Clock clock) {
        this.userService = userService;
        this.familyService = familyService;
        this.paymentAccountService = paymentAccountService;
        this.expenseService = expenseService;
        this.clock = clock;
    }

    @Transactional
    public UserService.LoginResult login() {
        UserService.LoginResult result = userService.loginAsDemo();
        Long userId = result.userId();

        // 群組名稱須全系統唯一，以 Demo 帳號的 userId 區分
        FamilyGroup group = familyService.createGroup(userId, "使用者的家 #" + userId);
        FamilyMember[] members = new FamilyMember[MEMBER_NAMES.length + 1];
        members[0] = familyService.getMyMembership(userId).orElseThrow();
        for (int i = 0; i < MEMBER_NAMES.length; i++) {
            Long memberUserId = userService.createDemoMember(MEMBER_NAMES[i]).getId();
            members[i + 1] = familyService.joinGroup(memberUserId, group.getInviteCode());
        }

        PaymentAccount[] accounts = new PaymentAccount[ACCOUNT_NAMES.length];
        for (int i = 0; i < ACCOUNT_NAMES.length; i++) {
            accounts[i] = paymentAccountService.create(group.getId(), userId, ACCOUNT_NAMES[i]);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDate today = now.toLocalDate();
        // 從上個月 1 日到今天（不產生未來日期），每天隨機 0–MAX_PER_DAY 筆
        for (LocalDate day = today.minusMonths(1).withDayOfMonth(1);
                !day.isAfter(today);
                day = day.plusDays(1)) {
            int count = random.nextInt(MAX_PER_DAY + 1);
            for (int i = 0; i < count; i++) {
                SampleExpense sample = SAMPLES[random.nextInt(SAMPLES.length)];
                FamilyMember author = members[random.nextInt(members.length)];
                createExpense(group, author, accounts, sample, randomTimeOn(now, day));
            }
        }
        return result;
    }

    private void createExpense(
            FamilyGroup group,
            FamilyMember member,
            PaymentAccount[] accounts,
            SampleExpense sample,
            LocalDateTime occurredAt) {
        expenseService.create(
                group.getId(),
                member.getUserId(),
                accounts[sample.accountIndex()].getId(),
                BigDecimal.valueOf(sample.amount()),
                sample.note(),
                occurredAt);
    }

    // 隨機時間：當天不得晚於現在（避免未來時間），其餘日子落在 07:00–22:59
    private static LocalDateTime randomTimeOn(LocalDateTime now, LocalDate day) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int secondOfDay =
                day.equals(now.toLocalDate())
                        ? random.nextInt(Math.max(1, now.toLocalTime().toSecondOfDay()))
                        : random.nextInt(7 * 3600, 23 * 3600);
        return day.atStartOfDay().plusSeconds(secondOfDay).withNano(0);
    }
}
