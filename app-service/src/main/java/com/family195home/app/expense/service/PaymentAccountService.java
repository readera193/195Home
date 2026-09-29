package com.family195home.app.expense.service;

import com.family195home.app.common.ApiException;
import com.family195home.app.expense.application.PaymentAccountRepository;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.service.FamilyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentAccountService {

    private final PaymentAccountRepository paymentAccountRepository;
    private final FamilyService familyService;

    public PaymentAccountService(PaymentAccountRepository paymentAccountRepository, FamilyService familyService) {
        this.paymentAccountRepository = paymentAccountRepository;
        this.familyService = familyService;
    }

    // FR-003, FR-018（群組已解散拒絕新增支付帳戶）
    public PaymentAccount create(Long familyGroupId, Long familyMemberId, String name) {
        var group = familyService.getGroup(familyGroupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "找不到家庭群組"));
        if (group.getStatus() == FamilyGroupStatus.DISSOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_DISSOLVED", "此家庭群組已解散，無法新增支付帳戶");
        }
        PaymentAccount account = new PaymentAccount(
                familyMemberId, familyGroupId, name, PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountRepository.save(account);
        return account;
    }

    public List<PaymentAccount> list(Long familyGroupId, PaymentAccountStatus status) {
        return paymentAccountRepository.findByGroupId(familyGroupId, status);
    }

    // FR-022：僅能軟停用，不允許真正刪除已被使用過的帳戶（本階段一律走軟停用，不提供刪除端點）
    public PaymentAccount disable(Long accountId) {
        PaymentAccount account = paymentAccountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "找不到支付帳戶"));
        account.setStatus(PaymentAccountStatus.DISABLED);
        paymentAccountRepository.update(account);
        return account;
    }
}
