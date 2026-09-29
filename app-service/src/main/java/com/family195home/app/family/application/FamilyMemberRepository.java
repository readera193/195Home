package com.family195home.app.family.application;

import com.family195home.app.family.domain.FamilyMember;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository {

    FamilyMember save(FamilyMember member);

    void update(FamilyMember member);

    Optional<FamilyMember> findById(Long id);

    /** 使用者目前是否已有一筆在職（ACTIVE）成員身分（FR-026，一使用者僅能同時屬於一個家庭群組）。 */
    Optional<FamilyMember> findActiveByUserId(Long userId);

    /** 使用者對某群組是否已存在成員紀錄（無論在職/已離開），供重新加入判斷使用（FR-025）。 */
    Optional<FamilyMember> findByUserIdAndGroupId(Long userId, Long familyGroupId);

    List<FamilyMember> findByGroupId(Long familyGroupId, boolean includeLeft);

    int countActiveByGroupId(Long familyGroupId);

    /** 群組內 joinedAt 最早的其他在職成員（管理者自動轉移用，FR-029）。 */
    Optional<FamilyMember> findEarliestOtherActiveMember(Long familyGroupId, Long excludeMemberId);
}
