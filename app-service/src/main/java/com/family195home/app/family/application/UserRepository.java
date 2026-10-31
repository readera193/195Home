package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    void updateDisplayName(Long id, String displayName);

    /** 自訂顯示名稱（使用者本人或群組管理者）：同時標記為已自訂，之後 LINE 登入不再覆蓋。 */
    void renameDisplayName(Long id, String displayName);

    Optional<User> findByLineUserId(String lineUserId);

    Optional<User> findById(Long id);

    List<User> findAll();

    /** 刪除 line_user_id 以 prefix 開頭、建立於 cutoff 之前、且已無任何群組成員身分的帳號（Demo 資料清理用）。 */
    int deleteOrphansByLineUserIdPrefix(String lineUserIdPrefix, LocalDateTime cutoff);
}
