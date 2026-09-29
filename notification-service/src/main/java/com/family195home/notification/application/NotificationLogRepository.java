package com.family195home.notification.application;

import com.family195home.notification.domain.NotificationLog;

import java.util.List;

public interface NotificationLogRepository {

    NotificationLog save(NotificationLog log);

    List<NotificationLog> findByGroupAndMonth(Long familyGroupId, String yearMonth);
}
