import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getMembers, getMyFamily } from '../services/familyApi';
import { listAccounts, listExpenses } from '../services/expenseApi';

export default function ExpenseListPage() {
  const { data: myFamily } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const { data: accounts } = useQuery({
    queryKey: ['accounts', familyGroupId, 'ALL'],
    queryFn: () => listAccounts(familyGroupId!, 'ALL'),
    enabled: !!familyGroupId,
  });
  const { data: members } = useQuery({
    queryKey: ['members', familyGroupId],
    queryFn: () => getMembers(familyGroupId!),
    enabled: !!familyGroupId,
  });

  const [paymentAccountId, setPaymentAccountId] = useState('');
  const [authorMemberId, setAuthorMemberId] = useState('');

  const { data: expenses, isLoading } = useQuery({
    queryKey: ['expenses', familyGroupId, paymentAccountId, authorMemberId],
    queryFn: () =>
      listExpenses({
        familyGroupId: familyGroupId!,
        paymentAccountId: paymentAccountId ? Number(paymentAccountId) : undefined,
        authorMemberId: authorMemberId ? Number(authorMemberId) : undefined,
      }),
    enabled: !!familyGroupId,
  });

  if (!familyGroupId) {
    return <p>您尚未加入任何家庭群組</p>;
  }

  return (
    <div>
      <h1>支出紀錄</h1>

      <div>
        <label htmlFor="filterAccount">支付帳戶篩選</label>
        <select id="filterAccount" value={paymentAccountId} onChange={(e) => setPaymentAccountId(e.target.value)}>
          <option value="">全部</option>
          {accounts?.map((account) => (
            <option key={account.accountId} value={account.accountId}>
              {account.name}
            </option>
          ))}
        </select>

        <label htmlFor="filterMember">成員篩選</label>
        <select id="filterMember" value={authorMemberId} onChange={(e) => setAuthorMemberId(e.target.value)}>
          <option value="">全部</option>
          {members?.map((member) => (
            <option key={member.familyMemberId} value={member.familyMemberId}>
              {member.email}
              {member.status !== 'ACTIVE' ? '（已離開）' : ''}
            </option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <p>載入中...</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>金額</th>
              <th>備註</th>
              <th>支付帳戶</th>
            </tr>
          </thead>
          <tbody>
            {expenses?.map((expense) => (
              <tr key={expense.expenseId}>
                <td>{new Date(expense.occurredAt).toLocaleString()}</td>
                <td>{expense.amount}</td>
                <td>{expense.note}</td>
                <td>{expense.paymentAccountName}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
