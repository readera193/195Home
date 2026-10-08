package com.family195home.app.family.application;

import com.family195home.app.shared.ApiException;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.LineBindingCode;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineBindingServiceTest {

    private LineBindingCodeRepository codeRepository;
    private LineBindingRepository bindingRepository;
    private FamilyMemberRepository familyMemberRepository;
    private LineBindingService lineBindingService;

    @BeforeEach
    void setUp() {
        codeRepository = mock(LineBindingCodeRepository.class);
        bindingRepository = mock(LineBindingRepository.class);
        familyMemberRepository = mock(FamilyMemberRepository.class);
        lineBindingService = new LineBindingService(codeRepository, bindingRepository, familyMemberRepository, 10L);
    }

    @Test
    void consume_rejectsExpiredCode() {
        // FR-023
        LineBindingCode expired = new LineBindingCode(1L, "123456", LocalDateTime.now().minusMinutes(1), false, LocalDateTime.now().minusMinutes(11));
        when(codeRepository.findByCode("123456")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> lineBindingService.consume("123456", "line-user-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CODE_EXPIRED_OR_USED"));
    }

    @Test
    void consume_rejectsAlreadyUsedCode() {
        // FR-023
        LineBindingCode used = new LineBindingCode(1L, "123456", LocalDateTime.now().plusMinutes(5), true, LocalDateTime.now());
        when(codeRepository.findByCode("123456")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> lineBindingService.consume("123456", "line-user-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CODE_EXPIRED_OR_USED"));
    }

    @Test
    void consume_rejectsWhenLineAccountAlreadyBoundToAnotherMember() {
        // FR-020：同一 LINE 帳號嘗試綁定至多個家庭成員身分時拒絕
        LineBindingCode valid = new LineBindingCode(1L, "123456", LocalDateTime.now().plusMinutes(5), false, LocalDateTime.now());
        when(codeRepository.findByCode("123456")).thenReturn(Optional.of(valid));
        when(bindingRepository.existsByLineUserId("line-user-1")).thenReturn(true);

        assertThatThrownBy(() -> lineBindingService.consume("123456", "line-user-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LINE_ACCOUNT_ALREADY_BOUND"));
    }

    @Test
    void consume_succeedsForValidUnusedCodeAndUnboundLineAccount() {
        LineBindingCode valid = new LineBindingCode(1L, "123456", LocalDateTime.now().plusMinutes(5), false, LocalDateTime.now());
        when(codeRepository.findByCode("123456")).thenReturn(Optional.of(valid));
        when(bindingRepository.existsByLineUserId("line-user-1")).thenReturn(false);
        FamilyMember member = new FamilyMember(20L, 200L, MemberStatus.ACTIVE, MemberRole.MEMBER, LocalDateTime.now());
        member.setId(1L);
        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        LineBindingService.BindingResult result = lineBindingService.consume("123456", "line-user-1");

        assertThat(result.familyMemberId()).isEqualTo(1L);
        assertThat(result.familyGroupId()).isEqualTo(20L);
    }
}
