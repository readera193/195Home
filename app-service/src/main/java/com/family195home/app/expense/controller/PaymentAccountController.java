package com.family195home.app.expense.controller;

import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.expense.dto.CreateAccountRequest;
import com.family195home.app.expense.dto.PaymentAccountResponse;
import com.family195home.app.expense.service.PaymentAccountService;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.service.FamilyService;
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
    private final FamilyService familyService;

    public PaymentAccountController(PaymentAccountService paymentAccountService, FamilyService familyService) {
        this.paymentAccountService = paymentAccountService;
        this.familyService = familyService;
    }

    @PostMapping
    public ResponseEntity<PaymentAccountResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateAccountRequest request) {
        // FR-017：呼叫者須為該家庭群組成員，familyMemberId 由呼叫者自身身分決定，不接受前端傳入
        FamilyMember member = familyService.assertMemberAuthorized(
                request.familyGroupId(), caller.userId(), FamilyService.RequiredRole.ANY_MEMBER);
        PaymentAccount account = paymentAccountService.create(request.familyGroupId(), member.getId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @GetMapping
    public ResponseEntity<List<PaymentAccountResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam Long familyGroupId,
            @RequestParam(required = false) String status) {
        // FR-017：呼叫者須為該家庭群組成員，否則回傳 403
        familyService.assertMemberAuthorized(familyGroupId, caller.userId(), FamilyService.RequiredRole.ANY_MEMBER);
        PaymentAccountStatus statusFilter = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null
                : PaymentAccountStatus.valueOf(status.toUpperCase());
        List<PaymentAccountResponse> accounts = paymentAccountService.list(familyGroupId, statusFilter).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/{accountId}/disable")
    public ResponseEntity<PaymentAccountResponse> disable(@PathVariable Long accountId) {
        PaymentAccount account = paymentAccountService.disable(accountId);
        return ResponseEntity.ok(toResponse(account));
    }

    private PaymentAccountResponse toResponse(PaymentAccount account) {
        return new PaymentAccountResponse(account.getId(), account.getFamilyMemberId(), account.getName(), account.getStatus().name());
    }
}
