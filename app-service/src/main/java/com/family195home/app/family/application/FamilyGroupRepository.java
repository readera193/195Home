package com.family195home.app.family.application;

import com.family195home.app.family.domain.FamilyGroup;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FamilyGroupRepository {

    boolean existsByName(String name);

    FamilyGroup save(FamilyGroup familyGroup);

    void update(FamilyGroup familyGroup);

    Optional<FamilyGroup> findById(Long id);

    Optional<FamilyGroup> findByInviteCode(String inviteCode);

    /** 建立於 cutoff 之前、且所有成員的 line_user_id 皆以 prefix 開頭的群組 id（Demo 資料清理用），最多 limit 筆。 */
    List<Long> findIdsCreatedBeforeWithOnlyPrefixedMembers(
            String lineUserIdPrefix, LocalDateTime cutoff, int limit);

    int deleteByIds(List<Long> ids);
}
