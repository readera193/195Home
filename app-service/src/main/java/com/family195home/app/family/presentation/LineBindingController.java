package com.family195home.app.family.presentation;

import com.family195home.app.family.application.LineBindingService;
import com.family195home.app.family.presentation.dto.BindingResponse;
import com.family195home.app.family.presentation.dto.LineBindingView;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 以下內部端點僅供 notification-service 呼叫（X-Internal-Token，見 InternalTokenFilter）。 */
@RestController
public class LineBindingController {

    private final LineBindingService lineBindingService;

    public LineBindingController(LineBindingService lineBindingService) {
        this.lineBindingService = lineBindingService;
    }

    @GetMapping("/api/internal/line-bindings/by-line-user/{lineUserId}")
    public ResponseEntity<BindingResponse> byLineUser(@PathVariable String lineUserId) {
        return lineBindingService.findByLineUserId(lineUserId)
                .map(result -> ResponseEntity.ok(new BindingResponse(result.familyMemberId(), result.familyGroupId())))
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "NOT_BOUND", "此 LINE 帳號尚未加入任何家庭"));
    }

    @GetMapping("/api/internal/line-bindings")
    public ResponseEntity<List<LineBindingView>> all() {
        List<LineBindingView> views = lineBindingService.findAllBoundMembers().stream()
                .map(b -> new LineBindingView(b.familyMemberId(), b.familyGroupId(), b.lineUserId()))
                .toList();
        return ResponseEntity.ok(views);
    }
}
