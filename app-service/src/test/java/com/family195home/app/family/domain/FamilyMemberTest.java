package com.family195home.app.family.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FamilyMemberTest {

    private FamilyMember admin() {
        return new FamilyMember(
                10L, 1L, MemberStatus.ACTIVE, MemberRole.ADMIN, LocalDateTime.now());
    }

    @Test
    void leave_dropsAdminRoleAndRecordsTime() {
        FamilyMember member = admin();
        LocalDateTime at = LocalDateTime.of(2026, 10, 1, 12, 0);

        member.leave(MemberStatus.LEFT, at);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(member.isAdmin()).isFalse();
        assertThat(member.getLeftAt()).isEqualTo(at);
    }

    @Test
    void rejoin_restoresActiveAndClearsLeftAt() {
        FamilyMember member = admin();
        member.leave(MemberStatus.LEFT, LocalDateTime.now());

        member.rejoin();

        assertThat(member.isActive()).isTrue();
        assertThat(member.getLeftAt()).isNull();
        assertThat(member.isAdmin()).isFalse();
    }

    @Test
    void allowRejoin_movesRemovedToLeft() {
        FamilyMember member = admin();
        member.leave(MemberStatus.REMOVED, LocalDateTime.now());

        member.allowRejoin();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.LEFT);
    }

    @Test
    void promoteAndDemote_changeRole() {
        FamilyMember member =
                new FamilyMember(
                        10L, 2L, MemberStatus.ACTIVE, MemberRole.MEMBER, LocalDateTime.now());

        member.promoteToAdmin();
        assertThat(member.isAdmin()).isTrue();

        member.demoteToMember();
        assertThat(member.isAdmin()).isFalse();
    }
}
