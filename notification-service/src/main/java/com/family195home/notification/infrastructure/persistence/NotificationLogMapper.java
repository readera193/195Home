package com.family195home.notification.infrastructure.persistence;

import com.family195home.notification.domain.NotificationLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationLogMapper {

    void insert(NotificationLog log);

    List<NotificationLog> selectByGroupAndMonth(
            @Param("familyGroupId") Long familyGroupId, @Param("yearMonth") String yearMonth);
}
