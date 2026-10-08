package com.family195home.app.family.presentation;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.family.domain.LineBindingCode;
import com.family195home.app.family.presentation.dto.BindingResponse;
import com.family195home.app.family.presentation.dto.ConsumeCodeRequest;
import com.family195home.app.family.presentation.dto.GenerateCodeResponse;
import com.family195home.app.family.presentation.dto.LineBindingView;
import com.family195home.app.family.application.LineBindingService;
import com.family195home.app.shared.AuthenticatedUser;
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


    public LineBindingController(LineBindingService lineBindingService) {
        this.lineBindingService = lineBindingService;

    }

    // FR-023：限本人產生自己的綁定碼
    @PostMapping("/api/families/members/{memberId}/line-binding-codes")
    public ResponseEntity<GenerateCodeResponse> generateCode(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long memberId) {
        LineBindingCode code = lineBindingService.generateCodeForCaller(memberId, caller.userId());
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
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "NOT_BOUND", "此 LINE 帳號尚未綁定"));
    }

    @GetMapping("/api/internal/line-bindings")
    public ResponseEntity<List<LineBindingView>> all() {
        List<LineBindingView> views = lineBindingService.findAllBoundMembers().stream()
                .map(b -> new LineBindingView(b.familyMemberId(), b.familyGroupId(), b.lineUserId()))
                .toList();
        return ResponseEntity.ok(views);
    }
}
