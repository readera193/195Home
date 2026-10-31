package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.FamilyGroup;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FamilyGroupMapper {

    int countByName(@Param("name") String name);

    void insert(FamilyGroup familyGroup);

    void update(FamilyGroup familyGroup);

    FamilyGroup selectById(@Param("id") Long id);

    FamilyGroup selectByInviteCode(@Param("inviteCode") String inviteCode);

    List<Long> selectIdsCreatedBeforeWithOnlyPrefixedMembers(
            @Param("prefix") String prefix,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    int deleteByIds(@Param("ids") List<Long> ids);
}
