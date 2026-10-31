import React, { useState } from 'react';
import { createExpense, type PaymentAccountResponse } from '../../services/expenseApi';
import { getErrorMessage } from '../../utils/apiError';
import { validateAmountAndNote } from '../../utils/expenseValidation';
import { notifyError } from '../../utils/notify';
import InfoTip from '../InfoTip';

interface CreateExpenseDialogProps {
  familyGroupId: number;
  /** 只能選擇啟用中的支付帳戶。 */
  activeAccounts?: PaymentAccountResponse[];
  onClose: () => void;
  onCreated: () => void | Promise<void>;
}

export default function CreateExpenseDialog({
  familyGroupId,
  activeAccounts,
  onClose,
  onCreated,
}: CreateExpenseDialogProps) {
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [accountId, setAccountId] = useState('');
  const [occurredAt, setOccurredAt] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const invalid = validateAmountAndNote(amount, note);
    if (invalid) {
      notifyError(invalid);
      return;
    }
    if (!accountId) {
      notifyError('請選擇支付帳戶');
      return;
    }

    setSubmitting(true);
    try {
      await createExpense({
        familyGroupId,
        paymentAccountId: Number(accountId),
        amount: Number(amount),
        note,
        occurredAt: occurredAt || undefined,
      });
      await onCreated();
    } catch (err) {
      notifyError(getErrorMessage(err, '新增支出失敗'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form
        className="card modal"
        role="dialog"
        aria-modal="true"
        aria-label="新增支出"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <h2>新增支出</h2>
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
            value={accountId}
            onChange={(e) => setAccountId(e.target.value)}
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
        <button
          type="button"
          className="btn-secondary btn-block"
          onClick={onClose}
          disabled={submitting}
        >
          取消
        </button>
      </form>
    </div>
  );
}
