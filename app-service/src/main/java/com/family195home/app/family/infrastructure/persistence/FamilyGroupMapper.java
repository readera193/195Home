package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.FamilyGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FamilyGroupMapper {

    int countByName(@Param("name") String name);

    void insert(FamilyGroup familyGroup);

    void update(FamilyGroup familyGroup);

    FamilyGroup selectById(@Param("id") Long id);

    FamilyGroup selectByInviteCode(@Param("inviteCode") String inviteCode);
}
