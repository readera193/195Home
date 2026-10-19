package com.family195home.app.family.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 供 notification-service 透過內部 API 查詢 LINE 身分對應的家庭成員。
 * LINE 身分即登入身分（users.line_user_id），不再有獨立的綁定流程。
 */
@Service
public class LineBindingService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public LineBindingService(UserRepository userRepository, FamilyMemberRepository familyMemberRepository) {
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    // FR-015：供 LINE 關鍵字查詢確認發話帳號是否屬於某家庭的有效成員
    @Transactional(readOnly = true)
    public Optional<BindingResult> findByLineUserId(String lineUserId) {
        return userRepository.findByLineUserId(lineUserId)
                .flatMap(user -> familyMemberRepository.findActiveByUserId(user.getId()))
                .map(member -> new BindingResult(member.getId(), member.getFamilyGroupId()));
    }

    // 供每月排程通知取得所有家庭的所有有效成員
    @Transactional(readOnly = true)
    public List<BoundMember> findAllBoundMembers() {
        return userRepository.findAll().stream()
                // Demo 帳號沒有真實 LINE ID，推播只會失敗
                .filter(user -> !user.getLineUserId().startsWith(UserService.DEMO_LINE_USER_ID_PREFIX))
                .flatMap(user -> familyMemberRepository.findActiveByUserId(user.getId())
                        .map(member -> new BoundMember(member.getId(), member.getFamilyGroupId(), user.getLineUserId()))
                        .stream())
                .toList();
    }

    public record BindingResult(Long familyMemberId, Long familyGroupId) {
    }

    public record BoundMember(Long familyMemberId, Long familyGroupId, String lineUserId) {
    }
}
