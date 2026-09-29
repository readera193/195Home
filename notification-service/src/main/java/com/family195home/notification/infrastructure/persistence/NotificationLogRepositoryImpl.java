package com.family195home.notification.infrastructure.persistence;

import com.family195home.notification.application.NotificationLogRepository;
import com.family195home.notification.domain.NotificationLog;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NotificationLogRepositoryImpl implements NotificationLogRepository {

    private final NotificationLogMapper mapper;

    public NotificationLogRepositoryImpl(NotificationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NotificationLog save(NotificationLog log) {
        mapper.insert(log);
        return log;
    }

    @Override
    public List<NotificationLog> findByGroupAndMonth(Long familyGroupId, String yearMonth) {
        return mapper.selectByGroupAndMonth(familyGroupId, yearMonth);
    }
}
