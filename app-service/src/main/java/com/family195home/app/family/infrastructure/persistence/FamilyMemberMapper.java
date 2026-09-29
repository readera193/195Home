package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.FamilyMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FamilyMemberMapper {

    void insert(FamilyMember member);

    void update(FamilyMember member);

    FamilyMember selectById(@Param("id") Long id);

    FamilyMember selectActiveByUserId(@Param("userId") Long userId);

    FamilyMember selectByUserIdAndGroupId(@Param("userId") Long userId, @Param("familyGroupId") Long familyGroupId);

    List<FamilyMember> selectByGroupId(@Param("familyGroupId") Long familyGroupId, @Param("includeLeft") boolean includeLeft);

    int countActiveByGroupId(@Param("familyGroupId") Long familyGroupId);

    FamilyMember selectEarliestOtherActiveMember(
            @Param("familyGroupId") Long familyGroupId, @Param("excludeMemberId") Long excludeMemberId);
}
