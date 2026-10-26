package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    void insert(User user);

    void updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    void renameDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    User selectByLineUserId(@Param("lineUserId") String lineUserId);

    User selectById(@Param("id") Long id);

    List<User> selectAll();
}
