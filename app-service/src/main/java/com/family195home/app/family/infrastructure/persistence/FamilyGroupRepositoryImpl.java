package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.application.FamilyGroupRepository;
import com.family195home.app.family.domain.FamilyGroup;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class FamilyGroupRepositoryImpl implements FamilyGroupRepository {

    private final FamilyGroupMapper mapper;

    public FamilyGroupRepositoryImpl(FamilyGroupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.countByName(name) > 0;
    }

    @Override
    public FamilyGroup save(FamilyGroup familyGroup) {
        mapper.insert(familyGroup);
        return familyGroup;
    }

    @Override
    public void update(FamilyGroup familyGroup) {
        mapper.update(familyGroup);
    }

    @Override
    public Optional<FamilyGroup> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<FamilyGroup> findByInviteCode(String inviteCode) {
        return Optional.ofNullable(mapper.selectByInviteCode(inviteCode));
    }
}
