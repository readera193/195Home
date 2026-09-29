package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.LineBindingCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LineBindingCodeMapper {

    void insert(LineBindingCode code);

    void markUsed(@Param("id") Long id);

    LineBindingCode selectByCode(@Param("code") String code);
}
