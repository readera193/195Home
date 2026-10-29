package com.family195home.app.demo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 清理過期 Demo 資料用的跨資料表 SQL（family / expense 兩個模組的表），因此獨立放在 demo 套件。 */
@Mapper
public interface DemoDataCleanupMapper {

    /** 建立於 cutoff 之前，且成員全是 Demo 帳號的群組（有真實 LINE 使用者加入的群組不會被選中）。 */
    List<Long> selectExpiredDemoGroupIds(
            @Param("prefix") String prefix, @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    int deleteExpensesByGroupIds(@Param("groupIds") List<Long> groupIds);

    int deletePaymentAccountsByGroupIds(@Param("groupIds") List<Long> groupIds);

    int deleteMembersByGroupIds(@Param("groupIds") List<Long> groupIds);

    int deleteGroupsByIds(@Param("groupIds") List<Long> groupIds);

    /** 刪除建立於 cutoff 之前、已無任何群組成員身分的 Demo 帳號。 */
    int deleteOrphanDemoUsers(@Param("prefix") String prefix, @Param("cutoff") LocalDateTime cutoff);
}
