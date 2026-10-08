package com.family195home.app.expense.application;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.family.application.FamilyAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付帳戶為群組層級，新增／改名／停用／刪除一律限該群組管理者（FR-003、FR-022）。
 */
@Service
public class PaymentAccountService {

    private final PaymentAccountRepository paymentAccountRepository;
    private final ExpenseRecordRepository expenseRecordRepository;
    private final FamilyAccess familyAccess;

    public PaymentAccountService(
            PaymentAccountRepository paymentAccountRepository,
            ExpenseRecordRepository expenseRecordRepository,
            FamilyAccess familyAccess) {
        this.paymentAccountRepository = paymentAccountRepository;
        this.expenseRecordRepository = expenseRecordRepository;
        this.familyAccess = familyAccess;
    }

    // FR-003（限 ADMIN）, FR-018（群組已解散拒絕新增支付帳戶）
    public PaymentAccount create(Long familyGroupId, Long callerUserId, String name) {
        FamilyAccess.MemberRef admin = familyAccess.requireAdmin(familyGroupId, callerUserId);
        var groupState = familyAccess.groupState(familyGroupId);
        if (groupState == FamilyAccess.GroupState.NOT_FOUND) {
            throw new ApiException(ErrorKind.NOT_FOUND, "GROUP_NOT_FOUND", "找不到家庭群組");
        }
        if (groupState == FamilyAccess.GroupState.DISSOLVED) {
            throw new ApiException(ErrorKind.CONFLICT, "GROUP_DISSOLVED", "此家庭群組已解散，無法新增支付帳戶");
        }
        PaymentAccount account = new PaymentAccount(
                admin.memberId(), familyGroupId, name, PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountRepository.save(account);
        return account;
    }

    @Transactional(readOnly = true)
    public List<PaymentAccount> list(Long familyGroupId, PaymentAccountStatus status) {
        return paymentAccountRepository.findByGroupId(familyGroupId, status);
    }

    // FR-003（限 ADMIN）
    public PaymentAccount rename(Long accountId, Long callerUserId, String name) {
        PaymentAccount account = requireAccountAsAdmin(accountId, callerUserId);
        account.setName(name);
        paymentAccountRepository.update(account);
        return account;
    }

    // FR-022（限 ADMIN）：軟停用，既有支出紀錄仍顯示原帳戶名稱
    public PaymentAccount disable(Long accountId, Long callerUserId) {
        PaymentAccount account = requireAccountAsAdmin(accountId, callerUserId);
        account.setStatus(PaymentAccountStatus.DISABLED);
        paymentAccountRepository.update(account);
        return account;
    }

    // FR-022（限 ADMIN）：僅從未被支出紀錄使用的帳戶可真正刪除
    public void delete(Long accountId, Long callerUserId) {
        PaymentAccount account = requireAccountAsAdmin(accountId, callerUserId);
        if (expenseRecordRepository.existsByPaymentAccountId(accountId)) {
            throw new ApiException(ErrorKind.CONFLICT, "ACCOUNT_IN_USE", "此支付帳戶已有支出紀錄，無法刪除，請改用停用");
        }
        paymentAccountRepository.delete(account.getId());
    }

    private PaymentAccount requireAccountAsAdmin(Long accountId, Long callerUserId) {
        PaymentAccount account = paymentAccountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "ACCOUNT_NOT_FOUND", "找不到支付帳戶"));
        familyAccess.requireAdmin(account.getFamilyGroupId(), callerUserId);
        return account;
    }
}
