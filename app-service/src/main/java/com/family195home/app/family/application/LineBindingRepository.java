package com.family195home.app.family.application;

import com.family195home.app.family.domain.LineBinding;

import java.util.List;
import java.util.Optional;

public interface LineBindingRepository {

    LineBinding save(LineBinding binding);

    boolean existsByLineUserId(String lineUserId);

    Optional<LineBinding> findByFamilyMemberId(Long familyMemberId);

    Optional<LineBinding> findByLineUserId(String lineUserId);

    /** 供每月排程通知使用：取得所有家庭的所有 LINE 綁定關係。 */
    List<LineBinding> findAll();
}
