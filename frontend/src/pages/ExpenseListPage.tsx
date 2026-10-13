import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMembers, getMyFamily } from '../services/familyApi';
import { createExpense, listAccounts, listExpenses } from '../services/expenseApi';
import { getErrorMessage } from '../utils/apiError';
import InfoTip from '../components/InfoTip';

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

  const { data: activeAccounts } = useQuery({
    queryKey: ['accounts', familyGroupId, 'ACTIVE'],
    queryFn: () => listAccounts(familyGroupId!, 'ACTIVE'),
    enabled: !!familyGroupId,
  });
  const queryClient = useQueryClient();

  const [paymentAccountId, setPaymentAccountId] = useState('');
  const [authorMemberId, setAuthorMemberId] = useState('');
  const [page, setPage] = useState(0);

  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [formAccountId, setFormAccountId] = useState('');
  const [occurredAt, setOccurredAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!familyGroupId) return;
    setError(null);

    // FR-016：前端整數驗證（後端仍會再次驗證）
    if (!/^-?\d+$/.test(amount)) {
      setError('金額必須為整數，不支援小數點');
      return;
    }
    if (!note.trim()) {
      setError('備註為必填欄位');
      return;
    }
    if (!formAccountId) {
      setError('請選擇支付帳戶');
      return;
    }

    setSubmitting(true);
    try {
      await createExpense({
        familyGroupId,
        paymentAccountId: Number(formAccountId),
        amount: Number(amount),
        note,
        occurredAt: occurredAt || undefined,
      });
      setAmount('');
      setNote('');
      setOccurredAt('');
      setPage(0);
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
    } catch (err) {
      setError(getErrorMessage(err, '新增支出失敗'));
    } finally {
      setSubmitting(false);
    }
  }

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

      <form className="card" onSubmit={handleSubmit}>
        <h2>新增支出</h2>
        {error && <p role="alert">{error}</p>}
        <div className="field">
          <div className="label-row">
            <label htmlFor="amount">金額</label>
            <InfoTip text="請輸入整數：正數為流入、負數為流出（例如 -350），0 表示無金額異動。不支援小數點。" />
          </div>
          <input id="amount" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="note">說明</label>
          <input id="note" value={note} onChange={(e) => setNote(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="paymentAccountId">支付帳戶</label>
          <select
            id="paymentAccountId"
            value={formAccountId}
            onChange={(e) => setFormAccountId(e.target.value)}
            required
          >
            <option value="">請選擇</option>
            {activeAccounts?.map((account) => (
              <option key={account.accountId} value={account.accountId}>
                {account.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <div className="label-row">
            <label htmlFor="occurredAt">日期時間</label>
            <InfoTip text="留空則以現在時間記錄。" />
          </div>
          <input
            id="occurredAt"
            type="datetime-local"
            value={occurredAt}
            onChange={(e) => setOccurredAt(e.target.value)}
          />
        </div>
        <button type="submit" className="btn-block" disabled={submitting}>
          新增
        </button>
      </form>

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
                {member.displayName}
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
                <th>說明</th>
                <th className="num">金額</th>
                <th>支付帳戶</th>
              </tr>
            </thead>
            <tbody>
              {expenses?.map((expense) => (
                <tr key={expense.expenseId}>
                  <td>
                    <div>{new Date(expense.occurredAt).toLocaleDateString('zh-TW')}</div>
                    <div>
                      {new Date(expense.occurredAt).toLocaleTimeString('zh-TW', { hourCycle: 'h23', hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </div>
                  </td>
                  <td>{expense.note}</td>
                  <td className={`num ${expense.amount > 0 ? 'positive' : expense.amount < 0 ? 'negative' : ''}`}>
                    {expense.amount}
                  </td>
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
