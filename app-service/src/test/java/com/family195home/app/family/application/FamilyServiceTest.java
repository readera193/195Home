package com.family195home.app.family.application;

import com.family195home.app.shared.ApiException;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FamilyServiceTest {

    private FamilyGroupRepository familyGroupRepository;
    private FamilyMemberRepository familyMemberRepository;
    private UserRepository userRepository;
    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyGroupRepository = mock(FamilyGroupRepository.class);
        familyMemberRepository = mock(FamilyMemberRepository.class);
        userRepository = mock(UserRepository.class);
        familyService = new FamilyService(familyGroupRepository, familyMemberRepository, userRepository, new InviteCodeGenerator());
    }

    @Test
    void createGroup_rejectsDuplicateName() {
        // FR-001
        when(familyMemberRepository.findActiveByUserId(1L)).thenReturn(Optional.empty());
        when(familyGroupRepository.existsByName("王家")).thenReturn(true);

        assertThatThrownBy(() -> familyService.createGroup(1L, "王家"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("GROUP_NAME_TAKEN"));
    }

    @Test
    void createGroup_rejectsWhenAlreadyInAGroup() {
        // FR-026
        when(familyMemberRepository.findActiveByUserId(1L))
                .thenReturn(Optional.of(activeMember(1L, 10L, MemberRole.MEMBER)));

        assertThatThrownBy(() -> familyService.createGroup(1L, "王家"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_IN_A_GROUP"));
    }

    @Test
    void joinGroup_rejectsWhenAlreadyInAGroup() {
        // FR-026
        when(familyMemberRepository.findActiveByUserId(2L))
                .thenReturn(Optional.of(activeMember(99L, 10L, MemberRole.MEMBER)));

        assertThatThrownBy(() -> familyService.joinGroup(2L, "CODE1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_IN_A_GROUP"));
    }

    @Test
    void joinGroup_restoresLeftMemberToActive_forRejoin() {
        // FR-025：已離開成員以原邀請碼重新加入，恢復為 ACTIVE 並沿用原 FamilyMember id
        when(familyMemberRepository.findActiveByUserId(2L)).thenReturn(Optional.empty());
        FamilyGroup group = groupOf(10L, "王家", FamilyGroupStatus.ACTIVE, "CODE1");
        when(familyGroupRepository.findByInviteCode("CODE1")).thenReturn(Optional.of(group));

        FamilyMember leftMember = activeMember(5L, 10L, MemberRole.MEMBER);
        leftMember.setStatus(MemberStatus.LEFT);
        leftMember.setLeftAt(LocalDateTime.now().minusDays(1));
        when(familyMemberRepository.findByUserIdAndGroupId(2L, 10L)).thenReturn(Optional.of(leftMember));

        FamilyMember result = familyService.joinGroup(2L, "CODE1");

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(result.getLeftAt()).isNull();
        verify(familyMemberRepository).update(leftMember);
        verify(familyMemberRepository, never()).save(any());
    }

    @Test
    void joinGroup_rejectsDissolvedGroup() {
        when(familyMemberRepository.findActiveByUserId(2L)).thenReturn(Optional.empty());
        when(familyGroupRepository.findByInviteCode("CODE1"))
                .thenReturn(Optional.of(groupOf(10L, "王家", FamilyGroupStatus.DISSOLVED, "CODE1")));

        assertThatThrownBy(() -> familyService.joinGroup(2L, "CODE1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("GROUP_DISSOLVED"));
    }

    @Test
    void leave_solelyActiveMember_dissolvesGroup() {
        // FR-018
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(1);
        when(familyGroupRepository.findById(10L))
                .thenReturn(Optional.of(groupOf(10L, "王家", FamilyGroupStatus.ACTIVE, "CODE1")));

        FamilyService.LeaveResult result = familyService.leave(10L, 1L, admin.getUserId(), null);

        assertThat(result.familyGroupStatus()).isEqualTo(FamilyGroupStatus.DISSOLVED);
        ArgumentCaptor<FamilyGroup> captor = ArgumentCaptor.forClass(FamilyGroup.class);
        verify(familyGroupRepository).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FamilyGroupStatus.DISSOLVED);
    }

    @Test
    void leave_adminWithOtherActiveMembers_transfersAdminToDesignatedMember() {
        // FR-029
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember designated = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(designated));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);

        FamilyService.LeaveResult result = familyService.leave(10L, 1L, admin.getUserId(), 2L);

        assertThat(result.familyGroupStatus()).isEqualTo(FamilyGroupStatus.ACTIVE);
        assertThat(result.newAdminMemberId()).isEqualTo(2L);
        assertThat(designated.getRole()).isEqualTo(MemberRole.ADMIN);
        // 離開者不再持有管理者角色
        assertThat(admin.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(admin.getRole()).isEqualTo(MemberRole.MEMBER);
        verify(familyGroupRepository, never()).update(any());
    }

    @Test
    void leave_adminWithOtherActiveMembers_rejectsWhenNoNewAdminDesignated() {
        // FR-029：必須指定接任者，且驗證失敗時不得變更離開者狀態
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);

        assertThatThrownBy(() -> familyService.leave(10L, 1L, admin.getUserId(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NEW_ADMIN_REQUIRED"));
        assertThat(admin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(familyMemberRepository, never()).update(any());
    }

    @Test
    void leave_adminWithOtherActiveMembers_rejectsInactiveOrSelfDesignation() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember gone = activeMember(2L, 10L, MemberRole.MEMBER);
        gone.setStatus(MemberStatus.LEFT);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(gone));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);

        assertThatThrownBy(() -> familyService.leave(10L, 1L, admin.getUserId(), 2L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("MEMBER_NOT_ACTIVE"));
        assertThatThrownBy(() -> familyService.leave(10L, 1L, admin.getUserId(), 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CANNOT_TRANSFER_TO_SELF"));
        assertThat(admin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void leave_regularMember_doesNotNeedNewAdmin() {
        FamilyMember member = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);

        FamilyService.LeaveResult result = familyService.leave(10L, 2L, member.getUserId(), null);

        assertThat(result.newAdminMemberId()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.LEFT);
    }

    @Test
    void transferAdmin_swapsRolesBetweenCallerAndTarget() {
        // FR-032
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember target = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(target));

        FamilyMember result = familyService.transferAdmin(10L, admin.getUserId(), 2L);

        assertThat(result.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(admin.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(admin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void transferAdmin_rejectsNonAdminCaller() {
        FamilyMember caller = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(caller.getUserId())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> familyService.transferAdmin(10L, caller.getUserId(), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
    }

    @Test
    void transferAdmin_rejectsSelfTarget() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> familyService.transferAdmin(10L, admin.getUserId(), 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CANNOT_TRANSFER_TO_SELF"));
        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    void leave_rejectsWhenNotSelf() {
        FamilyMember member = activeMember(1L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyService.leave(10L, 1L, 999L, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_SELF"));
    }

    @Test
    void kick_rejectsWhenCallerNotAdmin() {
        // FR-028
        FamilyMember caller = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(caller.getUserId())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> familyService.kick(10L, 3L, caller.getUserId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
    }

    @Test
    void kick_byAdmin_marksTargetRemoved() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember target = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(target));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);

        FamilyMember result = familyService.kick(10L, 2L, admin.getUserId());

        assertThat(result.getStatus()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    void joinGroup_rejectsRemovedMember() {
        // FR-030：被移出的成員不可用邀請碼重新加入
        when(familyMemberRepository.findActiveByUserId(2L)).thenReturn(Optional.empty());
        when(familyGroupRepository.findByInviteCode("CODE1"))
                .thenReturn(Optional.of(groupOf(10L, "王家", FamilyGroupStatus.ACTIVE, "CODE1")));
        FamilyMember removed = activeMember(5L, 10L, MemberRole.MEMBER);
        removed.setStatus(MemberStatus.REMOVED);
        when(familyMemberRepository.findByUserIdAndGroupId(2L, 10L)).thenReturn(Optional.of(removed));

        assertThatThrownBy(() -> familyService.joinGroup(2L, "CODE1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("MEMBER_REMOVED"));
        assertThat(removed.getStatus()).isEqualTo(MemberStatus.REMOVED);
        verify(familyMemberRepository, never()).update(any());
    }

    @Test
    void restoreEligibility_byAdmin_changesRemovedToLeft() {
        // FR-030
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember removed = activeMember(2L, 10L, MemberRole.MEMBER);
        removed.setStatus(MemberStatus.REMOVED);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(removed));

        FamilyMember result = familyService.restoreEligibility(10L, 2L, admin.getUserId());

        assertThat(result.getStatus()).isEqualTo(MemberStatus.LEFT);
        verify(familyMemberRepository).update(removed);
    }

    @Test
    void restoreEligibility_rejectsNonRemovedMember() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember left = activeMember(2L, 10L, MemberRole.MEMBER);
        left.setStatus(MemberStatus.LEFT);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> familyService.restoreEligibility(10L, 2L, admin.getUserId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("MEMBER_NOT_REMOVED"));
    }

    @Test
    void restoreEligibility_rejectsNonAdmin() {
        FamilyMember member = activeMember(3L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(member.getUserId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyService.restoreEligibility(10L, 2L, member.getUserId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
    }

    @Test
    void requireMember_rejectsNonMember() {
        // FR-017
        when(familyMemberRepository.findActiveByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.requireMember(10L, 99L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_MEMBER"));
    }

    @Test
    void requireAdmin_rejectsRegularMember() {
        // FR-019
        FamilyMember member = activeMember(1L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(member.getUserId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyService.requireAdmin(10L, member.getUserId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
    }

    @Test
    void requireAdmin_allowsAdmin() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));

        FamilyAccess.MemberRef result = familyService.requireAdmin(10L, admin.getUserId());

        assertThat(result.memberId()).isEqualTo(admin.getId());
    }

    @Test
    void renameMember_allowsSelf() {
        FamilyMember member = activeMember(1L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(member.getUserId())).thenReturn(Optional.of(member));
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        familyService.renameMember(10L, 1L, member.getUserId(), " 小明 ");

        verify(userRepository).renameDisplayName(member.getUserId(), "小明");
    }

    @Test
    void renameMember_rejectsRegularMemberRenamingOthers() {
        FamilyMember caller = activeMember(1L, 10L, MemberRole.MEMBER);
        FamilyMember other = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(caller.getUserId())).thenReturn(Optional.of(caller));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> familyService.renameMember(10L, 2L, caller.getUserId(), "新名"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
        verify(userRepository, never()).renameDisplayName(any(), any());
    }

    @Test
    void renameMember_adminCanRenameOthers() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember other = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(other));

        familyService.renameMember(10L, 2L, admin.getUserId(), "新名");

        verify(userRepository).renameDisplayName(other.getUserId(), "新名");
    }

    private FamilyMember activeMember(Long id, Long groupId, MemberRole role) {
        FamilyMember member = new FamilyMember(groupId, id * 100, MemberStatus.ACTIVE, role, LocalDateTime.now());
        member.setId(id);
        return member;
    }

    private FamilyGroup groupOf(Long id, String name, FamilyGroupStatus status, String inviteCode) {
        FamilyGroup group = new FamilyGroup(name, status, inviteCode, LocalDateTime.now());
        group.setId(id);
        return group;
    }
}
