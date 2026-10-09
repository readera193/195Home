import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getMembers, getMyFamily } from '../services/familyApi';
import { listAccounts, listExpenses } from '../services/expenseApi';

const PAGE_SIZE = 20;

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
  const [page, setPage] = useState(0);

  const { data: expensePage, isLoading } = useQuery({
    queryKey: ['expenses', familyGroupId, paymentAccountId, authorMemberId, page],
    queryFn: () =>
      listExpenses({
        familyGroupId: familyGroupId!,
        paymentAccountId: paymentAccountId ? Number(paymentAccountId) : undefined,
        authorMemberId: authorMemberId ? Number(authorMemberId) : undefined,
        page,
        size: PAGE_SIZE,
      }),
    enabled: !!familyGroupId,
    placeholderData: keepPreviousData,
  });
  const expenses = expensePage?.items;
  const totalPages = expensePage?.totalPages ?? 0;

  if (!familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <h1>支出紀錄</h1>
      </div>

      <div className="card filters">
        <div className="field">
          <label htmlFor="filterAccount">支付帳戶篩選</label>
          <select
            id="filterAccount"
            value={paymentAccountId}
            onChange={(e) => {
              setPaymentAccountId(e.target.value);
              setPage(0);
            }}
          >
            <option value="">全部</option>
            {accounts?.map((account) => (
              <option key={account.accountId} value={account.accountId}>
                {account.name}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="filterMember">成員篩選</label>
          <select
            id="filterMember"
            value={authorMemberId}
            onChange={(e) => {
              setAuthorMemberId(e.target.value);
              setPage(0);
            }}
          >
            <option value="">全部</option>
            {members?.map((member) => (
              <option key={member.familyMemberId} value={member.familyMemberId}>
                {member.email}
                {member.status !== 'ACTIVE' ? '（已離開）' : ''}
              </option>
            ))}
          </select>
        </div>
      </div>

      {isLoading ? (
        <p className="empty">載入中...</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th className="num">金額</th>
                <th>備註</th>
                <th>支付帳戶</th>
              </tr>
            </thead>
            <tbody>
              {expenses?.map((expense) => (
                <tr key={expense.expenseId}>
                  <td>{new Date(expense.occurredAt).toLocaleString()}</td>
                  <td className={`num ${expense.amount > 0 ? 'positive' : expense.amount < 0 ? 'negative' : ''}`}>
                    {expense.amount}
                  </td>
                  <td>{expense.note}</td>
                  <td>
                    <span className="badge">{expense.paymentAccountName}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav className="pagination" aria-label="分頁">
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setPage((p) => p - 1)}
            disabled={page === 0}
          >
            上一頁
          </button>
          <span>
            第 {page + 1} / {totalPages} 頁（共 {expensePage?.totalElements} 筆）
          </span>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setPage((p) => p + 1)}
            disabled={page + 1 >= totalPages}
          >
            下一頁
          </button>
        </nav>
      )}
    </div>
  );
}
