package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import com.family195home.app.family.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 驗證 family 模組 Mapper 的自訂 SQL 於真實 MySQL 的語意（見 tasks.md T071/T096）。 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class FamilyMapperIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appdb")
            .withUsername("app")
            .withPassword("app");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FamilyGroupMapper familyGroupMapper;
    @Autowired
    private FamilyMemberMapper familyMemberMapper;

    @Test
    void memberQueries_filterByStatusAndOrderByJoinedAt() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        FamilyGroup group = new FamilyGroup("成員查詢家庭", FamilyGroupStatus.ACTIVE, "FMCODE01", base);
        familyGroupMapper.insert(group);

        FamilyMember first = newMember(group, "line-fm-1", MemberStatus.ACTIVE, MemberRole.ADMIN, base);
        FamilyMember second = newMember(group, "line-fm-2", MemberStatus.ACTIVE, MemberRole.MEMBER, base.plusDays(1));
        FamilyMember left = newMember(group, "line-fm-3", MemberStatus.LEFT, MemberRole.MEMBER, base.plusDays(2));

        assertThat(familyMemberMapper.countActiveByGroupId(group.getId())).isEqualTo(2);
        assertThat(familyMemberMapper.selectByGroupId(group.getId(), false))
                .extracting(FamilyMember::getId).containsExactly(first.getId(), second.getId());
        assertThat(familyMemberMapper.selectByGroupId(group.getId(), true))
                .extracting(FamilyMember::getId).containsExactly(first.getId(), second.getId(), left.getId());
        assertThat(familyMemberMapper.selectActiveByUserId(left.getUserId())).isNull();
        assertThat(familyMemberMapper.selectActiveByUserId(second.getUserId()).getId()).isEqualTo(second.getId());
        assertThat(familyMemberMapper.selectEarliestOtherActiveMember(group.getId(), first.getId()).getId())
                .isEqualTo(second.getId());
    }

    @Test
    void groupQueries_findByInviteCodeAndCountByName() {
        FamilyGroup group = new FamilyGroup("群組查詢家庭", FamilyGroupStatus.ACTIVE, "FGCODE01", LocalDateTime.now());
        familyGroupMapper.insert(group);

        assertThat(familyGroupMapper.countByName("群組查詢家庭")).isEqualTo(1);
        assertThat(familyGroupMapper.countByName("不存在的家庭")).isZero();
        assertThat(familyGroupMapper.selectByInviteCode("FGCODE01").getId()).isEqualTo(group.getId());
        assertThat(familyGroupMapper.selectByInviteCode("NOPE0000")).isNull();
    }

    private FamilyMember newMember(FamilyGroup group, String lineUserId, MemberStatus status, MemberRole role,
                                   LocalDateTime joinedAt) {
        User user = new User(lineUserId, lineUserId, joinedAt);
        userMapper.insert(user);
        FamilyMember member = new FamilyMember(group.getId(), user.getId(), status, role, joinedAt);
        familyMemberMapper.insert(member);
        return member;
    }
}
