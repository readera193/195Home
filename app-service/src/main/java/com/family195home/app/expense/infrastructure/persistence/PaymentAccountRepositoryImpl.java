package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.application.PaymentAccountRepository;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PaymentAccountRepositoryImpl implements PaymentAccountRepository {

    private final PaymentAccountMapper mapper;

    public PaymentAccountRepositoryImpl(PaymentAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PaymentAccount save(PaymentAccount account) {
        mapper.insert(account);
        return account;
    }

    @Override
    public void update(PaymentAccount account) {
        mapper.update(account);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public Optional<PaymentAccount> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<PaymentAccount> findByGroupId(Long familyGroupId, PaymentAccountStatus status) {
        return mapper.selectByGroupId(familyGroupId, status == null ? null : status.name());
    }
}
