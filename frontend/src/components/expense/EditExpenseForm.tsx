import React, { useState } from 'react';
import {
  updateExpense,
  type ExpenseResponse,
  type PaymentAccountResponse,
} from '../../services/expenseApi';
import { getErrorMessage } from '../../utils/apiError';
import { validateAmountAndNote } from '../../utils/expenseValidation';
import { notifyError } from '../../utils/notify';
import Modal from '../Modal';

interface EditExpenseFormProps {
  familyGroupId: number;
  expense: ExpenseResponse;
  /** 含已停用帳戶：既有紀錄可能仍使用已停用的帳戶。 */
  accounts?: PaymentAccountResponse[];
  /** 取消編輯；由呼叫端負責釋放編輯鎖。 */
  onCancel: () => void | Promise<void>;
  onSaved: () => void | Promise<void>;
}

export default function EditExpenseForm({
  familyGroupId,
  expense,
  accounts,
  onCancel,
  onSaved,
}: EditExpenseFormProps) {
  const [amount, setAmount] = useState(String(expense.amount));
  const [note, setNote] = useState(expense.note);
  const [accountId, setAccountId] = useState(String(expense.paymentAccountId));
  const [occurredAt, setOccurredAt] = useState(expense.occurredAt.slice(0, 16));
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const invalid = validateAmountAndNote(amount, note);
    if (invalid) {
      notifyError(invalid);
      return;
    }

    setSaving(true);
    try {
      await updateExpense(expense.expenseId, familyGroupId, {
        paymentAccountId: Number(accountId),
        amount: Number(amount),
        note,
        occurredAt: occurredAt || undefined,
      });
      await onSaved();
    } catch (err) {
      notifyError(getErrorMessage(err, '更新支出失敗'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal label="編輯支出" onClose={onCancel} onSubmit={handleSubmit} busy={saving}>
      <h2>編輯支出</h2>
      <div className="field">
        <label htmlFor="editAmount">編輯金額</label>
        <input
          id="editAmount"
          inputMode="numeric"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          required
          autoFocus
        />
      </div>
      <div className="field">
        <label htmlFor="editNote">編輯說明</label>
        <input id="editNote" value={note} onChange={(e) => setNote(e.target.value)} required />
      </div>
      <div className="field">
        <label htmlFor="editAccount">編輯支付帳戶</label>
        <select
          id="editAccount"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
          required
        >
          {accounts?.map((account) => (
            <option key={account.accountId} value={account.accountId}>
              {account.name}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor="editOccurredAt">編輯日期時間</label>
        <input
          id="editOccurredAt"
          type="datetime-local"
          value={occurredAt}
          onChange={(e) => setOccurredAt(e.target.value)}
        />
      </div>
      <button type="submit" className="btn-block" disabled={saving}>
        儲存
      </button>
      <button
        type="button"
        className="btn-secondary btn-block"
        onClick={onCancel}
        disabled={saving}
      >
        取消
      </button>
    </Modal>
  );
}
