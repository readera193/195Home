package com.family195home.app.expense.application;

import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentAccountRepository {

    PaymentAccount save(PaymentAccount account);

    void update(PaymentAccount account);

    void delete(Long id);

    Optional<PaymentAccount> findById(Long id);

    /** status 為 null 時回傳所有狀態（對應 API 的 status=ALL）。 */
    List<PaymentAccount> findByGroupId(Long familyGroupId, PaymentAccountStatus status);
}
