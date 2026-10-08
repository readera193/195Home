package com.family195home.app.expense.presentation;

import com.family195home.app.shared.PagedResult;
import com.family195home.app.expense.application.PaymentAccountRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.presentation.dto.CreateExpenseRequest;
import com.family195home.app.expense.presentation.dto.ExpenseResponse;
import com.family195home.app.expense.presentation.dto.LockResponse;
import com.family195home.app.expense.presentation.dto.UpdateExpenseRequest;
import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.family.application.FamilyAccess;
import com.family195home.app.shared.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final FamilyAccess familyAccess;
    private final PaymentAccountRepository paymentAccountRepository;

    public ExpenseController(ExpenseService expenseService, FamilyAccess familyAccess, PaymentAccountRepository paymentAccountRepository) {
        this.expenseService = expenseService;
        this.familyAccess = familyAccess;
        this.paymentAccountRepository = paymentAccountRepository;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateExpenseRequest request) {
        FamilyAccess.MemberRef member = familyAccess.requireMember(request.familyGroupId(), caller.userId());
        LocalDateTime occurredAt = request.occurredAt() != null && !request.occurredAt().isBlank()
                ? LocalDateTime.parse(request.occurredAt())
                : null;
        ExpenseRecord record = expenseService.create(
                request.familyGroupId(), member.memberId(), request.paymentAccountId(), request.amount(), request.note(), occurredAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
    }

    @GetMapping
    public ResponseEntity<PagedResult<ExpenseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam Long familyGroupId,
            @RequestParam(required = false) Long paymentAccountId,
            @RequestParam(required = false) Long authorMemberId,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ExpenseService.DEFAULT_PAGE_SIZE) int size) {
        // FR-017：家庭範圍隔離，呼叫者須為該家庭群組成員，否則回傳 403
        familyAccess.requireMember(familyGroupId, caller.userId());
        // 帳戶名稱整個群組只查一次（避免每筆紀錄各查一次 N+1）
        Map<Long, String> accountNames = paymentAccountRepository.findByGroupId(familyGroupId, null).stream()
                .collect(Collectors.toMap(PaymentAccount::getId, PaymentAccount::getName));
        PagedResult<ExpenseResponse> responses = expenseService
                .listPage(familyGroupId, paymentAccountId, authorMemberId, month, page, size)
                .map(record -> toResponse(record, accountNames::get));
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long expenseId,
            @RequestParam Long familyGroupId,
            @Valid @RequestBody UpdateExpenseRequest request) {
        LocalDateTime occurredAt = request.occurredAt() != null && !request.occurredAt().isBlank()
                ? LocalDateTime.parse(request.occurredAt())
                : null;
        ExpenseRecord record = expenseService.update(
                familyGroupId, expenseId, caller.userId(), request.paymentAccountId(), request.amount(), request.note(), occurredAt);
        return ResponseEntity.ok(toResponse(record));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long expenseId,
            @RequestParam Long familyGroupId) {
        expenseService.delete(familyGroupId, expenseId, caller.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{expenseId}/lock")
    public ResponseEntity<LockResponse> lock(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long expenseId,
            @RequestParam Long familyGroupId) {
        FamilyAccess.MemberRef member = familyAccess.requireMember(familyGroupId, caller.userId());
        ExpenseRecord record = expenseService.lock(expenseId, member.memberId());
        return ResponseEntity.ok(new LockResponse(record.getId(), record.getLockedByMemberId(), record.getLockedAt()));
    }

    @PostMapping("/{expenseId}/unlock")
    public ResponseEntity<Void> unlock(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long expenseId) {
        expenseService.unlock(expenseId, caller.userId());
        return ResponseEntity.noContent().build();
    }

    private ExpenseResponse toResponse(ExpenseRecord record) {
        return toResponse(record, id -> paymentAccountRepository.findById(id).map(PaymentAccount::getName).orElse(null));
    }

    private ExpenseResponse toResponse(ExpenseRecord record, Function<Long, String> accountNameLookup) {
        boolean locked = expenseService.isCurrentlyLocked(record);
        return new ExpenseResponse(
                record.getId(), record.getAmount(), record.getNote(), record.getOccurredAt(),
                record.getPaymentAccountId(), accountNameLookup.apply(record.getPaymentAccountId()),
                record.getAuthorMemberId(), locked);
    }
}
