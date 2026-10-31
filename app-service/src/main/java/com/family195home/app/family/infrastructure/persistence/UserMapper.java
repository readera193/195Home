package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    void insert(User user);

    void updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    void renameDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    User selectByLineUserId(@Param("lineUserId") String lineUserId);

    User selectById(@Param("id") Long id);

    List<User> selectAll();

    int deleteOrphansByLineUserIdPrefix(
            @Param("prefix") String prefix, @Param("cutoff") LocalDateTime cutoff);
}
