package com.family195home.app.family.presentation;

import com.family195home.app.family.application.UserRepository;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.presentation.dto.CreateFamilyGroupRequest;
import com.family195home.app.family.presentation.dto.FamilyGroupResponse;
import com.family195home.app.family.presentation.dto.JoinFamilyGroupRequest;
import com.family195home.app.family.presentation.dto.JoinFamilyGroupResponse;
import com.family195home.app.family.presentation.dto.KickResponse;
import com.family195home.app.family.presentation.dto.LeaveResponse;
import com.family195home.app.family.presentation.dto.MemberView;
import com.family195home.app.family.presentation.dto.MyFamilyResponse;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.shared.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final FamilyService familyService;
    private final UserRepository userRepository;

    public FamilyController(FamilyService familyService, UserRepository userRepository) {
        this.familyService = familyService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<MyFamilyResponse> me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return familyService.getMyMembership(caller.userId())
                .flatMap(member -> familyService.getGroup(member.getFamilyGroupId())
                        .map(group -> new MyFamilyResponse(
                                group.getId(), group.getName(), group.getStatus().name(), member.getRole().name(),
                                group.getInviteCode(), member.getId())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(MyFamilyResponse.none()));
    }

    @PostMapping
    public ResponseEntity<FamilyGroupResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateFamilyGroupRequest request) {
        FamilyGroup group = familyService.createGroup(caller.userId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FamilyGroupResponse(group.getId(), group.getName(), group.getInviteCode(), "ADMIN"));
    }

    @PostMapping("/join")
    public ResponseEntity<JoinFamilyGroupResponse> join(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody JoinFamilyGroupRequest request) {
        FamilyMember member = familyService.joinGroup(caller.userId(), request.inviteCode());
        return ResponseEntity.ok(new JoinFamilyGroupResponse(
                member.getFamilyGroupId(), null, member.getRole().name(), member.getStatus().name()));
    }

    @GetMapping("/{familyGroupId}/members")
    public ResponseEntity<List<MemberView>> members(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long familyGroupId,
            @RequestParam(defaultValue = "true") boolean includeLeft) {
        // FR-017：呼叫者必須本身即為該家庭群組成員，避免跨家庭資料外洩
        familyService.assertMemberAuthorized(familyGroupId, caller.userId(), FamilyService.RequiredRole.ANY_MEMBER);
        List<MemberView> views = familyService.getMembers(familyGroupId, includeLeft).stream()
                .map(this::toView)
                .toList();
        return ResponseEntity.ok(views);
    }

    @PostMapping("/{familyGroupId}/members/{memberId}/leave")
    public ResponseEntity<LeaveResponse> leave(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long familyGroupId,
            @PathVariable Long memberId) {
        FamilyService.LeaveResult result = familyService.leave(familyGroupId, memberId, caller.userId());
        return ResponseEntity.ok(new LeaveResponse(
                result.status().name(), result.familyGroupStatus().name(), result.newAdminMemberId()));
    }

    @PostMapping("/{familyGroupId}/members/{memberId}/kick")
    public ResponseEntity<KickResponse> kick(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long familyGroupId,
            @PathVariable Long memberId) {
        FamilyMember target = familyService.kick(familyGroupId, memberId, caller.userId());
        return ResponseEntity.ok(new KickResponse(target.getId(), target.getStatus().name()));
    }

    @PostMapping("/{familyGroupId}/members/{memberId}/restore-eligibility")
    public ResponseEntity<KickResponse> restoreEligibility(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long familyGroupId,
            @PathVariable Long memberId) {
        FamilyMember target = familyService.restoreEligibility(familyGroupId, memberId, caller.userId());
        return ResponseEntity.ok(new KickResponse(target.getId(), target.getStatus().name()));
    }

    private MemberView toView(FamilyMember member) {
        String email = userRepository.findById(member.getUserId()).map(u -> u.getEmail()).orElse(null);
        return new MemberView(
                member.getId(),
                member.getUserId(),
                email,
                member.getStatus().name(),
                member.getRole().name(),
                member.getJoinedAt(),
                member.getLeftAt());
    }
}
