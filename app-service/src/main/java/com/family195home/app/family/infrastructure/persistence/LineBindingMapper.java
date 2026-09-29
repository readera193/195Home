package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.LineBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LineBindingMapper {

    void insert(LineBinding binding);

    int countByLineUserId(@Param("lineUserId") String lineUserId);

    LineBinding selectByFamilyMemberId(@Param("familyMemberId") Long familyMemberId);

    LineBinding selectByLineUserId(@Param("lineUserId") String lineUserId);

    List<LineBinding> selectAll();
}
