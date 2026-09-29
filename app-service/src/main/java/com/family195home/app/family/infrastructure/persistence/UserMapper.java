package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int countByEmail(@Param("email") String email);

    void insert(User user);

    User selectByEmail(@Param("email") String email);

    User selectById(@Param("id") Long id);
}
