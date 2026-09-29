package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.application.FamilyMemberRepository;
import com.family195home.app.family.domain.FamilyMember;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FamilyMemberRepositoryImpl implements FamilyMemberRepository {

    private final FamilyMemberMapper mapper;

    public FamilyMemberRepositoryImpl(FamilyMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FamilyMember save(FamilyMember member) {
        mapper.insert(member);
        return member;
    }

    @Override
    public void update(FamilyMember member) {
        mapper.update(member);
    }

    @Override
    public Optional<FamilyMember> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<FamilyMember> findActiveByUserId(Long userId) {
        return Optional.ofNullable(mapper.selectActiveByUserId(userId));
    }

    @Override
    public Optional<FamilyMember> findByUserIdAndGroupId(Long userId, Long familyGroupId) {
        return Optional.ofNullable(mapper.selectByUserIdAndGroupId(userId, familyGroupId));
    }

    @Override
    public List<FamilyMember> findByGroupId(Long familyGroupId, boolean includeLeft) {
        return mapper.selectByGroupId(familyGroupId, includeLeft);
    }

    @Override
    public int countActiveByGroupId(Long familyGroupId) {
        return mapper.countActiveByGroupId(familyGroupId);
    }

    @Override
    public Optional<FamilyMember> findEarliestOtherActiveMember(Long familyGroupId, Long excludeMemberId) {
        return Optional.ofNullable(mapper.selectEarliestOtherActiveMember(familyGroupId, excludeMemberId));
    }
}
