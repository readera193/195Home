package com.family195home.app.family;

import com.family195home.app.common.ApiException;
import com.family195home.app.family.application.FamilyGroupRepository;
import com.family195home.app.family.application.FamilyMemberRepository;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import com.family195home.app.family.service.FamilyService;
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
    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyGroupRepository = mock(FamilyGroupRepository.class);
        familyMemberRepository = mock(FamilyMemberRepository.class);
        familyService = new FamilyService(familyGroupRepository, familyMemberRepository);
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

        FamilyService.LeaveResult result = familyService.leave(10L, 1L, admin.getUserId());

        assertThat(result.familyGroupStatus()).isEqualTo(FamilyGroupStatus.DISSOLVED);
        ArgumentCaptor<FamilyGroup> captor = ArgumentCaptor.forClass(FamilyGroup.class);
        verify(familyGroupRepository).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FamilyGroupStatus.DISSOLVED);
    }

    @Test
    void leave_adminWithOtherActiveMembers_transfersAdminToEarliestJoinedMember() {
        // FR-029
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember earliestOther = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);
        when(familyMemberRepository.findEarliestOtherActiveMember(10L, 1L)).thenReturn(Optional.of(earliestOther));

        FamilyService.LeaveResult result = familyService.leave(10L, 1L, admin.getUserId());

        assertThat(result.familyGroupStatus()).isEqualTo(FamilyGroupStatus.ACTIVE);
        assertThat(result.newAdminMemberId()).isEqualTo(2L);
        assertThat(earliestOther.getRole()).isEqualTo(MemberRole.ADMIN);
        verify(familyGroupRepository, never()).update(any());
    }

    @Test
    void leave_rejectsWhenNotSelf() {
        FamilyMember member = activeMember(1L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyService.leave(10L, 1L, 999L))
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
    void kick_byAdmin_marksTargetLeft() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        FamilyMember target = activeMember(2L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));
        when(familyMemberRepository.findById(2L)).thenReturn(Optional.of(target));
        when(familyMemberRepository.countActiveByGroupId(10L)).thenReturn(2);

        FamilyMember result = familyService.kick(10L, 2L, admin.getUserId());

        assertThat(result.getStatus()).isEqualTo(MemberStatus.LEFT);
    }

    @Test
    void assertMemberAuthorized_anyMember_rejectsNonMember() {
        // FR-017
        when(familyMemberRepository.findActiveByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.assertMemberAuthorized(10L, 99L, FamilyService.RequiredRole.ANY_MEMBER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_MEMBER"));
    }

    @Test
    void assertMemberAuthorized_admin_rejectsRegularMember() {
        // FR-019
        FamilyMember member = activeMember(1L, 10L, MemberRole.MEMBER);
        when(familyMemberRepository.findActiveByUserId(member.getUserId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyService.assertMemberAuthorized(10L, member.getUserId(), FamilyService.RequiredRole.ADMIN))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
    }

    @Test
    void assertMemberAuthorized_admin_allowsAdmin() {
        FamilyMember admin = activeMember(1L, 10L, MemberRole.ADMIN);
        when(familyMemberRepository.findActiveByUserId(admin.getUserId())).thenReturn(Optional.of(admin));

        FamilyMember result = familyService.assertMemberAuthorized(10L, admin.getUserId(), FamilyService.RequiredRole.ADMIN);

        assertThat(result).isEqualTo(admin);
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
