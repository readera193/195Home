package com.family195home.app.expense.controller;

import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.expense.dto.CreateAccountRequest;
import com.family195home.app.expense.dto.PaymentAccountResponse;
import com.family195home.app.expense.dto.UpdateAccountRequest;
import com.family195home.app.expense.service.PaymentAccountService;
import com.family195home.app.family.application.FamilyAccess;
import com.family195home.app.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class PaymentAccountController {

    private final PaymentAccountService paymentAccountService;
    private final FamilyAccess familyAccess;

    public PaymentAccountController(PaymentAccountService paymentAccountService, FamilyAccess familyAccess) {
        this.paymentAccountService = paymentAccountService;
        this.familyAccess = familyAccess;
    }

    @PostMapping
    public ResponseEntity<PaymentAccountResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateAccountRequest request) {
        // FR-003：限群組管理者；建立者身分由呼叫者自身決定，不接受前端傳入
        PaymentAccount account = paymentAccountService.create(request.familyGroupId(), caller.userId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @GetMapping
    public ResponseEntity<List<PaymentAccountResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam Long familyGroupId,
            @RequestParam(required = false) String status) {
        // FR-017：呼叫者須為該家庭群組成員，否則回傳 403
        familyAccess.requireMember(familyGroupId, caller.userId());
        PaymentAccountStatus statusFilter = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null
                : PaymentAccountStatus.valueOf(status.toUpperCase());
        List<PaymentAccountResponse> accounts = paymentAccountService.list(familyGroupId, statusFilter).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<PaymentAccountResponse> rename(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(toResponse(paymentAccountService.rename(accountId, caller.userId(), request.name())));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long accountId) {
        paymentAccountService.delete(accountId, caller.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/disable")
    public ResponseEntity<PaymentAccountResponse> disable(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long accountId) {
        PaymentAccount account = paymentAccountService.disable(accountId, caller.userId());
        return ResponseEntity.ok(toResponse(account));
    }

    private PaymentAccountResponse toResponse(PaymentAccount account) {
        return new PaymentAccountResponse(account.getId(), account.getFamilyMemberId(), account.getName(), account.getStatus().name());
    }
}
