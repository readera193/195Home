package com.family195home.app.family.application;

import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import com.family195home.app.family.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineBindingServiceTest {

    private UserRepository userRepository;
    private FamilyMemberRepository familyMemberRepository;
    private LineBindingService lineBindingService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        familyMemberRepository = mock(FamilyMemberRepository.class);
        lineBindingService = new LineBindingService(userRepository, familyMemberRepository);
    }

    @Test
    void findByLineUserId_returnsActiveMembership() {
        User user = user(10L, "line-user-1");
        when(userRepository.findByLineUserId("line-user-1")).thenReturn(Optional.of(user));
        when(familyMemberRepository.findActiveByUserId(10L)).thenReturn(Optional.of(member(1L, 20L, 10L)));

        Optional<LineBindingService.BindingResult> result = lineBindingService.findByLineUserId("line-user-1");

        assertThat(result).isPresent();
        assertThat(result.get().familyMemberId()).isEqualTo(1L);
        assertThat(result.get().familyGroupId()).isEqualTo(20L);
    }

    @Test
    void findByLineUserId_emptyWhenUnknownLineAccount() {
        // FR-015：未登入過平台的 LINE 帳號不得取得資料
        when(userRepository.findByLineUserId("unknown")).thenReturn(Optional.empty());

        assertThat(lineBindingService.findByLineUserId("unknown")).isEmpty();
    }

    @Test
    void findByLineUserId_emptyWhenUserHasNoActiveFamily() {
        when(userRepository.findByLineUserId("line-user-1")).thenReturn(Optional.of(user(10L, "line-user-1")));
        when(familyMemberRepository.findActiveByUserId(10L)).thenReturn(Optional.empty());

        assertThat(lineBindingService.findByLineUserId("line-user-1")).isEmpty();
    }

    @Test
    void findAllBoundMembers_skipsUsersWithoutActiveFamily() {
        when(userRepository.findAll()).thenReturn(List.of(user(10L, "line-a"), user(11L, "line-b")));
        when(familyMemberRepository.findActiveByUserId(10L)).thenReturn(Optional.of(member(1L, 20L, 10L)));
        when(familyMemberRepository.findActiveByUserId(11L)).thenReturn(Optional.empty());

        List<LineBindingService.BoundMember> result = lineBindingService.findAllBoundMembers();

        assertThat(result).containsExactly(new LineBindingService.BoundMember(1L, 20L, "line-a"));
    }

    private static User user(Long id, String lineUserId) {
        User user = new User(lineUserId, "name", LocalDateTime.now());
        user.setId(id);
        return user;
    }

    private static FamilyMember member(Long id, Long groupId, Long userId) {
        FamilyMember member = new FamilyMember(groupId, userId, MemberStatus.ACTIVE, MemberRole.MEMBER, LocalDateTime.now());
        member.setId(id);
        return member;
    }
}
