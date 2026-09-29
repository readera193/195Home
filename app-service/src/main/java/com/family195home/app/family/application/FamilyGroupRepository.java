package com.family195home.app.family.application;

import com.family195home.app.family.domain.FamilyGroup;

import java.util.Optional;

public interface FamilyGroupRepository {

    boolean existsByName(String name);

    FamilyGroup save(FamilyGroup familyGroup);

    void update(FamilyGroup familyGroup);

    Optional<FamilyGroup> findById(Long id);

    Optional<FamilyGroup> findByInviteCode(String inviteCode);
}
