package com.family195home.app.expense.service;

import com.family195home.app.common.ApiException;
import com.family195home.app.common.ErrorKind;
import com.family195home.app.expense.application.ExpenseRecordRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.service.FamilyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支出紀錄核心邏輯（US2）。與 family 模組之間一律透過 {@link FamilyService} 的
 * public 方法同進程呼叫（見 research.md 決策 7），不透過 HTTP。
 */
@Service
@Transactional
public class ExpenseService {

    private final ExpenseRecordRepository expenseRecordRepository;
    private final FamilyService familyService;
    private final long lockTtlMinutes;

    public ExpenseService(
            ExpenseRecordRepository expenseRecordRepository,
            FamilyService familyService,
            @Value("${app.expense-lock.ttl-minutes}") long lockTtlMinutes) {
        this.expenseRecordRepository = expenseRecordRepository;
        this.familyService = familyService;
        this.lockTtlMinutes = lockTtlMinutes;
    }

    // FR-004, FR-005, FR-006, FR-016, FR-018
    public ExpenseRecord create(Long familyGroupId, Long authorMemberId, Long paymentAccountId, BigDecimal amount, String note, LocalDateTime occurredAt) {
        var group = familyService.getGroup(familyGroupId)
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "GROUP_NOT_FOUND", "找不到家庭群組"));
        if (group.getStatus() == FamilyGroupStatus.DISSOLVED) {
            throw new ApiException(ErrorKind.CONFLICT, "GROUP_DISSOLVED", "此家庭群組已解散，無法新增支出紀錄");
        }
        int intAmount = toIntegerAmount(amount);
        if (note == null || note.isBlank()) {
            throw new ApiException(ErrorKind.BAD_REQUEST, "NOTE_REQUIRED", "備註為必填欄位");
        }
        if (paymentAccountId == null) {
            throw new ApiException(ErrorKind.BAD_REQUEST, "PAYMENT_ACCOUNT_REQUIRED", "支付帳戶為必填欄位");
        }

        ExpenseRecord record = new ExpenseRecord();
        record.setFamilyGroupId(familyGroupId);
        record.setPaymentAccountId(paymentAccountId);
        record.setAuthorMemberId(authorMemberId);
        record.setAmount(intAmount);
        record.setNote(note);
        record.setOccurredAt(occurredAt != null ? occurredAt : LocalDateTime.now()); // FR-005/FR-006
        LocalDateTime now = LocalDateTime.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        expenseRecordRepository.save(record);
        return record;
    }

    public List<ExpenseRecord> list(Long familyGroupId, Long paymentAccountId, Long authorMemberId, String month) {
        return expenseRecordRepository.findByFilter(familyGroupId, paymentAccountId, authorMemberId, month);
    }

    public ExpenseRecord findRequired(Long id) {
        return expenseRecordRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "EXPENSE_NOT_FOUND", "找不到支出紀錄"));
    }

    // FR-027：條件式 UPDATE 取得鎖，5 分鐘 TTL（見 research.md 決策 3）
    public ExpenseRecord lock(Long expenseId, Long callerMemberId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = expenseRecordRepository.tryLock(expenseId, callerMemberId, now, lockTtlMinutes);
        if (updated == 0) {
            throw new ApiException(ErrorKind.CONFLICT, "RECORD_LOCKED", "此紀錄目前正被編輯中，請稍後再試");
        }
        return findRequired(expenseId);
    }

    public void unlock(Long expenseId) {
        expenseRecordRepository.releaseLock(expenseId);
    }

    public boolean isCurrentlyLocked(ExpenseRecord record) {
        return record.isLocked(LocalDateTime.now(), lockTtlMinutes);
    }

    // FR-019：群組任一在職成員皆可編輯/刪除任何紀錄；FR-027：須已持有鎖
    public ExpenseRecord update(Long familyGroupId, Long expenseId, Long callerUserId, Long paymentAccountId, BigDecimal amount, String note, LocalDateTime occurredAt) {
        ExpenseRecord record = requireRecordInGroup(familyGroupId, expenseId, callerUserId);
        requireLockHeldByCaller(record, familyGroupId, callerUserId);

        record.setPaymentAccountId(paymentAccountId);
        record.setAmount(toIntegerAmount(amount));
        record.setNote(note);
        if (occurredAt != null) {
            record.setOccurredAt(occurredAt);
        }
        record.setUpdatedAt(LocalDateTime.now());
        expenseRecordRepository.update(record);
        expenseRecordRepository.releaseLock(record.getId());
        return record;
    }

    public void delete(Long familyGroupId, Long expenseId, Long callerUserId) {
        ExpenseRecord record = requireRecordInGroup(familyGroupId, expenseId, callerUserId);
        requireLockHeldByCaller(record, familyGroupId, callerUserId);
        expenseRecordRepository.delete(expenseId);
    }

    // FR-017、FR-019：呼叫者須為該群組在職成員，且紀錄須屬於該群組；不限制新增者本人。
    // 紀錄屬於其他群組屬授權問題而非資源不存在，與 NOT_GROUP_MEMBER 一致回傳 403
    private ExpenseRecord requireRecordInGroup(Long familyGroupId, Long expenseId, Long callerUserId) {
        ExpenseRecord record = findRequired(expenseId);
        familyService.assertMemberAuthorized(familyGroupId, callerUserId, FamilyService.RequiredRole.ANY_MEMBER);
        if (!familyGroupId.equals(record.getFamilyGroupId())) {
            throw new ApiException(ErrorKind.FORBIDDEN, "EXPENSE_NOT_IN_GROUP", "此支出紀錄不屬於該家庭群組");
        }
        return record;
    }

    private void requireLockHeldByCaller(ExpenseRecord record, Long familyGroupId, Long callerUserId) {
        FamilyMember caller = familyService.assertMemberAuthorized(familyGroupId, callerUserId, FamilyService.RequiredRole.ANY_MEMBER);
        if (record.getLockedByMemberId() == null || !record.getLockedByMemberId().equals(caller.getId())) {
            throw new ApiException(ErrorKind.FORBIDDEN, "LOCK_NOT_HELD_BY_CALLER", "請先取得編輯鎖定後再操作");
        }
    }

    // FR-016：金額必須為整數（不接受小數點），可正可負可零
    private int toIntegerAmount(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new ApiException(ErrorKind.BAD_REQUEST, "AMOUNT_MUST_BE_INTEGER", "金額必須為整數，不支援小數點");
        }
        return amount.intValueExact();
    }
}
