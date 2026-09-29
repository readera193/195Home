package com.family195home.app.family.application;

import com.family195home.app.family.domain.LineBindingCode;

import java.util.Optional;

public interface LineBindingCodeRepository {

    LineBindingCode save(LineBindingCode code);

    void markUsed(Long id);

    Optional<LineBindingCode> findByCode(String code);
}
