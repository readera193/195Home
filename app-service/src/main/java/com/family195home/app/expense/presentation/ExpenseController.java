package com.family195home.app.expense.presentation;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.presentation.dto.CreateExpenseRequest;
import com.family195home.app.expense.presentation.dto.ExpenseResponse;
import com.family195home.app.expense.presentation.dto.LockResponse;
import com.family195home.app.expense.presentation.dto.UpdateExpenseRequest;
import com.family195home.app.shared.AuthenticatedUser;
import com.family195home.app.shared.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "支出紀錄", description = "支出的新增、分頁篩選、編輯與刪除；編輯與刪除須先取得編輯鎖")
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final PaymentAccountService paymentAccountService;

    public ExpenseController(
            ExpenseService expenseService, PaymentAccountService paymentAccountService) {
        this.expenseService = expenseService;
        this.paymentAccountService = paymentAccountService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseRecord record =
                expenseService.create(
                        request.familyGroupId(),
                        caller.userId(),
                        request.paymentAccountId(),
                        request.amount(),
                        request.note(),
                        request.occurredAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
    }

    @GetMapping
    public ResponseEntity<PagedResult<ExpenseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam Long familyGroupId,
            @RequestParam(required = false) Long paymentAccountId,
            @RequestParam(required = false) Long authorMemberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ExpenseService.DEFAULT_PAGE_SIZE) int size) {
        PagedResult<ExpenseRecord> records =
                expenseService.listPage(
                        familyGroupId,
                        caller.userId(),
                        paymentAccountId,
                        authorMemberId,
                        startDate,
                        endDate,
                        page,
                        size);
        // 帳戶名稱整個群組只查一次（避免每筆紀錄各查一次 N+1）；須在成員檢查之後才查
        Map<Long, String> accountNames = paymentAccountService.nameById(familyGroupId);
        return ResponseEntity.ok(records.map(record -> toResponse(record, accountNames)));
    }

    @Operation(summary = "更新支出", description = "須已持有編輯鎖；成功後自動釋放。更換支付帳戶時帳戶須屬於同群組且為啟用狀態。")
    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long expenseId,
            @RequestParam Long familyGroupId,
            @Valid @RequestBody UpdateExpenseRequest request) {
        ExpenseRecord record =
                expenseService.update(
                        familyGroupId,
                        expenseId,
                        caller.userId(),
                        request.paymentAccountId(),
                        request.amount(),
                        request.note(),
                        request.occurredAt());
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

    @Operation(
            summary = "取得編輯鎖",
            description = "以條件式 UPDATE 取得 5 分鐘的編輯鎖；紀錄正被他人編輯且鎖未逾時回 409 RECORD_LOCKED。編輯或刪除前必須先呼叫。")
    @PostMapping("/{expenseId}/lock")
    public ResponseEntity<LockResponse> lock(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long expenseId,
            @RequestParam Long familyGroupId) {
        ExpenseRecord record = expenseService.lock(familyGroupId, expenseId, caller.userId());
        return ResponseEntity.ok(
                new LockResponse(
                        record.getId(), record.getLockedByMemberId(), record.getLockedAt()));
    }

    @Operation(summary = "釋放編輯鎖", description = "取消編輯時釋放；未鎖定或鎖已逾時視為已釋放（冪等），鎖由他人持有則回 403。")
    @PostMapping("/{expenseId}/unlock")
    public ResponseEntity<Void> unlock(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long expenseId) {
        expenseService.unlock(expenseId, caller.userId());
        return ResponseEntity.noContent().build();
    }

    private ExpenseResponse toResponse(ExpenseRecord record) {
        return toResponse(record, paymentAccountService.nameById(record.getFamilyGroupId()));
    }

    private ExpenseResponse toResponse(ExpenseRecord record, Map<Long, String> accountNames) {
        boolean locked = expenseService.isCurrentlyLocked(record);
        return new ExpenseResponse(
                record.getId(),
                record.getAmount(),
                record.getNote(),
                record.getOccurredAt(),
                record.getPaymentAccountId(),
                accountNames.get(record.getPaymentAccountId()),
                record.getAuthorMemberId(),
                locked);
    }
}
