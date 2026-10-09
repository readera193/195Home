import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { createExpense, listAccounts } from '../services/expenseApi';
import { getErrorMessage } from '../utils/apiError';

export default function ExpenseFormPage() {
  const navigate = useNavigate();
  const { data: myFamily } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const { data: accounts } = useQuery({
    queryKey: ['accounts', familyGroupId, 'ACTIVE'],
    queryFn: () => listAccounts(familyGroupId!, 'ACTIVE'),
    enabled: !!familyGroupId,
  });

  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [paymentAccountId, setPaymentAccountId] = useState('');
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
    if (!paymentAccountId) {
      setError('請選擇支付帳戶');
      return;
    }

    setSubmitting(true);
    try {
      await createExpense({
        familyGroupId,
        paymentAccountId: Number(paymentAccountId),
        amount: Number(amount),
        note,
        occurredAt: occurredAt || undefined,
      });
      navigate('/expenses');
    } catch (err) {
      setError(getErrorMessage(err, '新增支出失敗'));
    } finally {
      setSubmitting(false);
    }
  }

  if (!familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }

  return (
    <div className="container-narrow">
      <div className="page-header">
        <h1>新增支出紀錄</h1>
      </div>
      <form className="card" onSubmit={handleSubmit}>
        {error && <p role="alert">{error}</p>}
        <div className="field">
          <label htmlFor="amount">金額（整數，正數為流入、負數為流出、0 表示無金額異動）</label>
          <input id="amount" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="note">備註</label>
          <input id="note" value={note} onChange={(e) => setNote(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="paymentAccountId">支付帳戶</label>
          <select
            id="paymentAccountId"
            value={paymentAccountId}
            onChange={(e) => setPaymentAccountId(e.target.value)}
            required
          >
            <option value="">請選擇</option>
            {accounts?.map((account) => (
              <option key={account.accountId} value={account.accountId}>
                {account.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="occurredAt">日期時間（留空則為現在）</label>
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
    </div>
  );
}
