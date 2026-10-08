package com.family195home.app.family.application;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 家庭群組核心業務邏輯（US1）。{@link #assertMemberAuthorized} 是
 * 供 expense、statistics 模組直接呼叫（同進程方法呼叫）確認呼叫者角色與在職狀態的
 * 唯一入口（見 research.md 決策 7）。
 */
@Service
public class FamilyService implements FamilyAccess {

    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 10;

    private final FamilyGroupRepository familyGroupRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final SecureRandom random = new SecureRandom();

    public FamilyService(FamilyGroupRepository familyGroupRepository, FamilyMemberRepository familyMemberRepository) {
        this.familyGroupRepository = familyGroupRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    // FR-001, FR-026
    @Transactional
    public FamilyGroup createGroup(Long userId, String name) {
        assertNotAlreadyInGroup(userId);
        if (familyGroupRepository.existsByName(name)) {
            throw new ApiException(ErrorKind.CONFLICT, "GROUP_NAME_TAKEN", "此家庭群組名稱已被使用");
        }
        FamilyGroup group = new FamilyGroup(name, FamilyGroupStatus.ACTIVE, generateUniqueInviteCode(), LocalDateTime.now());
        familyGroupRepository.save(group);
        FamilyMember admin = new FamilyMember(group.getId(), userId, MemberStatus.ACTIVE, MemberRole.ADMIN, LocalDateTime.now());
        familyMemberRepository.save(admin);
        return group;
    }

    // FR-002, FR-025, FR-026, FR-030
    @Transactional
    public FamilyMember joinGroup(Long userId, String inviteCode) {
        assertNotAlreadyInGroup(userId);
        FamilyGroup group = familyGroupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "INVALID_INVITE_CODE", "邀請碼無效"));
        if (group.getStatus() == FamilyGroupStatus.DISSOLVED) {
            throw new ApiException(ErrorKind.CONFLICT, "GROUP_DISSOLVED", "此家庭群組已解散");
        }

        // FR-025：已離開成員以原邀請碼重新加入，恢復為 ACTIVE 並沿用原 FamilyMember，保留歷史紀錄歸屬
        Optional<FamilyMember> existing = familyMemberRepository.findByUserIdAndGroupId(userId, group.getId());
        if (existing.isPresent()) {
            FamilyMember member = existing.get();
            // FR-030：被管理者移出的成員不可用邀請碼重新加入，須由管理者恢復加入資格
            if (member.getStatus() == MemberStatus.REMOVED) {
                throw new ApiException(ErrorKind.FORBIDDEN, "MEMBER_REMOVED", "您已被管理者移出此群組，無法再次加入");
            }
            member.setStatus(MemberStatus.ACTIVE);
            member.setLeftAt(null);
            familyMemberRepository.update(member);
            return member;
        }

        FamilyMember member = new FamilyMember(group.getId(), userId, MemberStatus.ACTIVE, MemberRole.MEMBER, LocalDateTime.now());
        familyMemberRepository.save(member);
        return member;
    }

    private void assertNotAlreadyInGroup(Long userId) {
        if (familyMemberRepository.findActiveByUserId(userId).isPresent()) {
            throw new ApiException(ErrorKind.CONFLICT, "ALREADY_IN_A_GROUP", "您已屬於一個家庭群組，請先離開才能建立或加入新群組");
        }
    }

    /** 取得目前使用者所屬家庭群組與成員資訊（若未加入任何群組則回傳 empty）。 */
    @Transactional(readOnly = true)
    public Optional<FamilyMember> getMyMembership(Long userId) {
        return familyMemberRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<FamilyGroup> getGroup(Long familyGroupId) {
        return familyGroupRepository.findById(familyGroupId);
    }

    // FR-007, FR-021
    @Transactional(readOnly = true)
    public List<FamilyMember> getMembers(Long familyGroupId, boolean includeLeft) {
        return familyMemberRepository.findByGroupId(familyGroupId, includeLeft);
    }

    // FR-018, FR-029
    @Transactional
    public LeaveResult leave(Long familyGroupId, Long memberId, Long callerUserId) {
        FamilyMember member = requireActiveMemberInGroup(familyGroupId, memberId);
        if (!member.getUserId().equals(callerUserId)) {
            throw new ApiException(ErrorKind.FORBIDDEN, "NOT_SELF", "僅能操作自己的成員身分");
        }
        return doLeave(member, MemberStatus.LEFT);
    }

    // FR-028
    @Transactional
    public FamilyMember kick(Long familyGroupId, Long memberId, Long callerUserId) {
        assertMemberAuthorized(familyGroupId, callerUserId, RequiredRole.ADMIN);
        FamilyMember target = requireActiveMemberInGroup(familyGroupId, memberId);
        if (target.getUserId().equals(callerUserId)) {
            throw new ApiException(ErrorKind.BAD_REQUEST, "CANNOT_KICK_SELF", "無法移出自己，請使用離開群組");
        }
        doLeave(target, MemberStatus.REMOVED);
        return target;
    }

    // FR-030：管理者恢復被移出成員的加入資格（REMOVED → LEFT），之後該成員方可依 FR-025 以邀請碼重新加入
    public FamilyMember restoreEligibility(Long familyGroupId, Long memberId, Long callerUserId) {
        assertMemberAuthorized(familyGroupId, callerUserId, RequiredRole.ADMIN);
        FamilyMember target = familyMemberRepository.findById(memberId)
                .filter(m -> m.getFamilyGroupId().equals(familyGroupId))
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "MEMBER_NOT_FOUND", "找不到成員"));
        if (target.getStatus() != MemberStatus.REMOVED) {
            throw new ApiException(ErrorKind.CONFLICT, "MEMBER_NOT_REMOVED", "此成員並非被移出狀態");
        }
        target.setStatus(MemberStatus.LEFT);
        familyMemberRepository.update(target);
        return target;
    }

    private FamilyMember requireActiveMemberInGroup(Long familyGroupId, Long memberId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .filter(m -> m.getFamilyGroupId().equals(familyGroupId))
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "MEMBER_NOT_FOUND", "找不到成員"));
        if (!member.isActive()) {
            throw new ApiException(ErrorKind.CONFLICT, "MEMBER_NOT_ACTIVE", "此成員目前不是在職狀態");
        }
        return member;
    }

    private LeaveResult doLeave(FamilyMember member, MemberStatus newStatus) {
        Long familyGroupId = member.getFamilyGroupId();
        int activeCountBeforeLeaving = familyMemberRepository.countActiveByGroupId(familyGroupId);

        member.setStatus(newStatus);
        member.setLeftAt(LocalDateTime.now());
        familyMemberRepository.update(member);

        // FR-018：唯一在職成員離開時，群組自動解散
        if (activeCountBeforeLeaving <= 1) {
            FamilyGroup group = familyGroupRepository.findById(familyGroupId)
                    .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "GROUP_NOT_FOUND", "找不到家庭群組"));
            group.setStatus(FamilyGroupStatus.DISSOLVED);
            familyGroupRepository.update(group);
            return new LeaveResult(newStatus, FamilyGroupStatus.DISSOLVED, null);
        }

        // FR-029：管理者離開仍有其他在職成員的群組時，自動轉移給 joinedAt 最早的其他在職成員
        Long newAdminMemberId = null;
        if (member.isAdmin()) {
            FamilyMember newAdmin = familyMemberRepository.findEarliestOtherActiveMember(familyGroupId, member.getId())
                    .orElseThrow(() -> new ApiException(ErrorKind.INTERNAL, "NO_ELIGIBLE_ADMIN", "找不到可接任的管理者"));
            newAdmin.setRole(MemberRole.ADMIN);
            familyMemberRepository.update(newAdmin);
            newAdminMemberId = newAdmin.getId();
        }
        return new LeaveResult(newStatus, FamilyGroupStatus.ACTIVE, newAdminMemberId);
    }

    /**
     * 同進程方法呼叫入口：確認 callerUserId 是否為
     * familyGroupId 的在職成員，並視需要要求 ADMIN 角色（FR-017、FR-019）。
     */
    @Transactional(readOnly = true)
    public FamilyMember assertMemberAuthorized(Long familyGroupId, Long callerUserId, RequiredRole requiredRole) {
        FamilyMember member = familyMemberRepository.findActiveByUserId(callerUserId)
                .filter(m -> m.getFamilyGroupId().equals(familyGroupId))
                .orElseThrow(() -> new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_MEMBER", "您不是該家庭群組成員"));
        if (requiredRole == RequiredRole.ADMIN && !member.isAdmin()) {
            throw new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_ADMIN", "僅群組管理者可執行此操作");
        }
        return member;
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = randomCode(INVITE_CODE_LENGTH);
        } while (familyGroupRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(INVITE_CODE_ALPHABET.charAt(random.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public MemberRef requireMember(Long familyGroupId, Long callerUserId) {
        return new MemberRef(assertMemberAuthorized(familyGroupId, callerUserId, RequiredRole.ANY_MEMBER).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public MemberRef requireAdmin(Long familyGroupId, Long callerUserId) {
        return new MemberRef(assertMemberAuthorized(familyGroupId, callerUserId, RequiredRole.ADMIN).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public GroupState groupState(Long familyGroupId) {
        return familyGroupRepository.findById(familyGroupId)
                .map(g -> g.getStatus() == FamilyGroupStatus.DISSOLVED ? GroupState.DISSOLVED : GroupState.ACTIVE)
                .orElse(GroupState.NOT_FOUND);
    }

    public enum RequiredRole {
        ANY_MEMBER,
        ADMIN
    }

    public record LeaveResult(MemberStatus status, FamilyGroupStatus familyGroupStatus, Long newAdminMemberId) {
    }
}
