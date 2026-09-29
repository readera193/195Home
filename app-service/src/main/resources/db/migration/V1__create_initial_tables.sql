-- appdb: family 模組
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE family_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    invite_code VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_family_groups_name UNIQUE (name),
    CONSTRAINT uk_family_groups_invite_code UNIQUE (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE family_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    CONSTRAINT fk_family_members_group FOREIGN KEY (family_group_id) REFERENCES family_groups(id),
    CONSTRAINT fk_family_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_family_members_group (family_group_id),
    INDEX idx_family_members_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE line_binding_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_member_id BIGINT NOT NULL,
    code VARCHAR(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_line_binding_codes_code UNIQUE (code),
    CONSTRAINT fk_line_binding_codes_member FOREIGN KEY (family_member_id) REFERENCES family_members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE line_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_member_id BIGINT NOT NULL,
    line_user_id VARCHAR(64) NOT NULL,
    bound_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_line_bindings_member UNIQUE (family_member_id),
    CONSTRAINT uk_line_bindings_line_user UNIQUE (line_user_id),
    CONSTRAINT fk_line_bindings_member FOREIGN KEY (family_member_id) REFERENCES family_members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- appdb: expense 模組
CREATE TABLE payment_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_member_id BIGINT NOT NULL,
    family_group_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_payment_accounts_group (family_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_group_id BIGINT NOT NULL,
    payment_account_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    amount INT NOT NULL,
    note VARCHAR(500) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    locked_by_member_id BIGINT NULL,
    locked_at DATETIME(6) NULL,
    CONSTRAINT fk_expense_records_account FOREIGN KEY (payment_account_id) REFERENCES payment_accounts(id),
    -- 對應 SC-003（篩選後列表 2 秒內回應）：涵蓋 FR-008/FR-009/FR-010 常用篩選欄位組合
    INDEX idx_expense_records_filter (family_group_id, payment_account_id, author_member_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
