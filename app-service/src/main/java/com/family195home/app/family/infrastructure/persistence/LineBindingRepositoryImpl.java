package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.application.LineBindingRepository;
import com.family195home.app.family.domain.LineBinding;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LineBindingRepositoryImpl implements LineBindingRepository {

    private final LineBindingMapper mapper;

    public LineBindingRepositoryImpl(LineBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LineBinding save(LineBinding binding) {
        mapper.insert(binding);
        return binding;
    }

    @Override
    public boolean existsByLineUserId(String lineUserId) {
        return mapper.countByLineUserId(lineUserId) > 0;
    }

    @Override
    public Optional<LineBinding> findByFamilyMemberId(Long familyMemberId) {
        return Optional.ofNullable(mapper.selectByFamilyMemberId(familyMemberId));
    }

    @Override
    public Optional<LineBinding> findByLineUserId(String lineUserId) {
        return Optional.ofNullable(mapper.selectByLineUserId(lineUserId));
    }

    @Override
    public List<LineBinding> findAll() {
        return mapper.selectAll();
    }
}
