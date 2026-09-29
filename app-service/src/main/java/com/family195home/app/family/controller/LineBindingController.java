package com.family195home.app.family.controller;

import com.family195home.app.common.ApiException;
import com.family195home.app.family.application.FamilyMemberRepository;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.LineBindingCode;
import com.family195home.app.family.dto.BindingResponse;
import com.family195home.app.family.dto.ConsumeCodeRequest;
import com.family195home.app.family.dto.GenerateCodeResponse;
import com.family195home.app.family.dto.LineBindingView;
import com.family195home.app.family.service.LineBindingService;
import com.family195home.app.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class LineBindingController {

    private final LineBindingService lineBindingService;
    private final FamilyMemberRepository familyMemberRepository;

    public LineBindingController(LineBindingService lineBindingService, FamilyMemberRepository familyMemberRepository) {
        this.lineBindingService = lineBindingService;
        this.familyMemberRepository = familyMemberRepository;
    }

    // FR-023：限本人產生自己的綁定碼
    @PostMapping("/api/families/members/{memberId}/line-binding-codes")
    public ResponseEntity<GenerateCodeResponse> generateCode(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long memberId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "找不到成員"));
        if (!member.getUserId().equals(caller.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_SELF", "僅能為自己產生綁定碼");
        }
        LineBindingCode code = lineBindingService.generateCode(memberId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GenerateCodeResponse(code.getCode(), DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(code.getExpiresAt())));
    }

    // 以下三個內部端點僅供 notification-service 呼叫（X-Internal-Token，見 InternalTokenFilter）

    @PostMapping("/api/internal/line-bindings")
    public ResponseEntity<BindingResponse> consume(@Valid @RequestBody ConsumeCodeRequest request) {
        LineBindingService.BindingResult result = lineBindingService.consume(request.code(), request.lineUserId());
        return ResponseEntity.ok(new BindingResponse(result.familyMemberId(), result.familyGroupId()));
    }

    @GetMapping("/api/internal/line-bindings/by-line-user/{lineUserId}")
    public ResponseEntity<BindingResponse> byLineUser(@PathVariable String lineUserId) {
        return lineBindingService.findByLineUserId(lineUserId)
                .map(result -> ResponseEntity.ok(new BindingResponse(result.familyMemberId(), result.familyGroupId())))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_BOUND", "此 LINE 帳號尚未綁定"));
    }

    @GetMapping("/api/internal/line-bindings")
    public ResponseEntity<List<LineBindingView>> all() {
        List<LineBindingView> views = lineBindingService.findAllBindings().stream()
                .map(binding -> {
                    Long familyGroupId = familyMemberRepository.findById(binding.getFamilyMemberId())
                            .map(FamilyMember::getFamilyGroupId)
                            .orElse(null);
                    return new LineBindingView(binding.getFamilyMemberId(), familyGroupId, binding.getLineUserId());
                })
                .toList();
        return ResponseEntity.ok(views);
    }
}
