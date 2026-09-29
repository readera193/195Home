package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.application.LineBindingCodeRepository;
import com.family195home.app.family.domain.LineBindingCode;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class LineBindingCodeRepositoryImpl implements LineBindingCodeRepository {

    private final LineBindingCodeMapper mapper;

    public LineBindingCodeRepositoryImpl(LineBindingCodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LineBindingCode save(LineBindingCode code) {
        mapper.insert(code);
        return code;
    }

    @Override
    public void markUsed(Long id) {
        mapper.markUsed(id);
    }

    @Override
    public Optional<LineBindingCode> findByCode(String code) {
        return Optional.ofNullable(mapper.selectByCode(code));
    }
}
