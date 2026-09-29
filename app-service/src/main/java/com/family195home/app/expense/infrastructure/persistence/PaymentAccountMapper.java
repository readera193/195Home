package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.domain.PaymentAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentAccountMapper {

    void insert(PaymentAccount account);

    void update(PaymentAccount account);

    PaymentAccount selectById(@Param("id") Long id);

    List<PaymentAccount> selectByGroupId(@Param("familyGroupId") Long familyGroupId, @Param("status") String status);
}
