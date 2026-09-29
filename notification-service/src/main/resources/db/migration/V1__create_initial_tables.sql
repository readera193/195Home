CREATE TABLE notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_group_id BIGINT NOT NULL,
    family_member_id BIGINT NOT NULL,
    line_user_id VARCHAR(64) NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_attempt_at DATETIME(6) NOT NULL,
    error_message VARCHAR(500) NULL,
    INDEX idx_notification_logs_group_month (family_group_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
