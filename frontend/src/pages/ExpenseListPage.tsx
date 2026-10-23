import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMembers, getMyFamily } from '../services/familyApi';
import {
  createExpense,
  deleteExpense,
  listAccounts,
  listExpenses,
  lockExpense,
  unlockExpense,
  updateExpense,
  type ExpenseResponse,
} from '../services/expenseApi';
import { getErrorMessage } from '../utils/apiError';
import InfoTip from '../components/InfoTip';

const PAGE_SIZE = 20;

/** 本月第一天（offset=0）或最後一天（offset=1），格式 YYYY-MM-DD（本地時區）。 */
function monthBoundary(offset: 0 | 1): string {
  const now = new Date();
  const d = offset === 0 ? new Date(now.getFullYear(), now.getMonth(), 1) : new Date(now.getFullYear(), now.getMonth() + 1, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

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
  const [startDate, setStartDate] = useState(() => monthBoundary(0));
  const [endDate, setEndDate] = useState(() => monthBoundary(1));
  const [page, setPage] = useState(0);

  const [showCreate, setShowCreate] = useState(false);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [formAccountId, setFormAccountId] = useState('');
  const [occurredAt, setOccurredAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 編輯／刪除（FR-019、FR-027）：先取得鎖才能操作，取消或完成時釋放
  const [editing, setEditing] = useState<ExpenseResponse | null>(null);
  const [editAmount, setEditAmount] = useState('');
  const [editNote, setEditNote] = useState('');
  const [editAccountId, setEditAccountId] = useState('');
  const [editOccurredAt, setEditOccurredAt] = useState('');
  const [editError, setEditError] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleStartEdit(expense: ExpenseResponse) {
    if (!familyGroupId) return;
    setRowError(null);
    try {
      await lockExpense(expense.expenseId, familyGroupId);
    } catch (err) {
      setRowError(getErrorMessage(err, '無法編輯此紀錄'));
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
      return;
    }
    setEditing(expense);
    setEditAmount(String(expense.amount));
    setEditNote(expense.note);
    setEditAccountId(String(expense.paymentAccountId));
    setEditOccurredAt(expense.occurredAt.slice(0, 16));
    setEditError(null);
  }

  async function handleCancelEdit() {
    if (!editing) return;
    const id = editing.expenseId;
    setEditing(null);
    try {
      await unlockExpense(id);
    } catch {
      // 釋放失敗無妨：鎖會於 TTL 後自動失效
    }
    await queryClient.invalidateQueries({ queryKey: ['expenses'] });
  }

  async function handleSaveEdit(e: React.FormEvent) {
    e.preventDefault();
    if (!familyGroupId || !editing) return;
    setEditError(null);
    if (!/^-?\d+$/.test(editAmount)) {
      setEditError('金額必須為整數，不支援小數點');
      return;
    }
    if (!editNote.trim()) {
      setEditError('說明為必填欄位');
      return;
    }
    setSaving(true);
    try {
      await updateExpense(editing.expenseId, familyGroupId, {
        paymentAccountId: Number(editAccountId),
        amount: Number(editAmount),
        note: editNote,
        occurredAt: editOccurredAt || undefined,
      });
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
    } catch (err) {
      setEditError(getErrorMessage(err, '更新支出失敗'));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(expense: ExpenseResponse) {
    if (!familyGroupId) return;
    setRowError(null);
    try {
      await lockExpense(expense.expenseId, familyGroupId);
    } catch (err) {
      setRowError(getErrorMessage(err, '無法刪除此紀錄'));
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
      return;
    }
    if (!window.confirm(`確定要刪除「${expense.note}」嗎？`)) {
      try {
        await unlockExpense(expense.expenseId);
      } catch {
        // 同上：鎖會自動逾時
      }
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
      return;
    }
    try {
      await deleteExpense(expense.expenseId, familyGroupId);
    } catch (err) {
      setRowError(getErrorMessage(err, '刪除支出失敗'));
    }
    await queryClient.invalidateQueries({ queryKey: ['expenses'] });
  }

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
      setError('說明為必填欄位');
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
      setShowCreate(false);
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
    } catch (err) {
      setError(getErrorMessage(err, '新增支出失敗'));
    } finally {
      setSubmitting(false);
    }
  }

  const { data: expensePage, isLoading } = useQuery({
    queryKey: ['expenses', familyGroupId, paymentAccountId, authorMemberId, startDate, endDate, page],
    queryFn: () =>
      listExpenses({
        familyGroupId: familyGroupId!,
        paymentAccountId: paymentAccountId ? Number(paymentAccountId) : undefined,
        authorMemberId: authorMemberId ? Number(authorMemberId) : undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
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
      <div className="page-header page-header--row">
        <h1>支出紀錄</h1>
        <button type="button" onClick={() => setShowCreate(true)}>
          新增支出
        </button>
      </div>

      {showCreate && (
      <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
      <form
        className="card modal"
        role="dialog"
        aria-modal="true"
        aria-label="新增支出"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
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
        <button
          type="button"
          className="btn-secondary btn-block"
          onClick={() => setShowCreate(false)}
          disabled={submitting}
        >
          取消
        </button>
      </form>
      </div>
      )}

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

        <div className="field">
          <label htmlFor="filterStartDate">起始日期</label>
          <input
            id="filterStartDate"
            type="date"
            value={startDate}
            max={endDate || undefined}
            onChange={(e) => {
              setStartDate(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="field">
          <label htmlFor="filterEndDate">結束日期</label>
          <input
            id="filterEndDate"
            type="date"
            value={endDate}
            min={startDate || undefined}
            onChange={(e) => {
              setEndDate(e.target.value);
              setPage(0);
            }}
          />
        </div>
      </div>

      {editing && (
        <form className="card" onSubmit={handleSaveEdit} aria-label="編輯支出">
          <h2>編輯支出</h2>
          {editError && <p role="alert">{editError}</p>}
          <div className="field">
            <label htmlFor="editAmount">編輯金額</label>
            <input id="editAmount" value={editAmount} onChange={(e) => setEditAmount(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="editNote">編輯說明</label>
            <input id="editNote" value={editNote} onChange={(e) => setEditNote(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="editAccount">編輯支付帳戶</label>
            <select id="editAccount" value={editAccountId} onChange={(e) => setEditAccountId(e.target.value)} required>
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
              value={editOccurredAt}
              onChange={(e) => setEditOccurredAt(e.target.value)}
            />
          </div>
          <button type="submit" disabled={saving}>
            儲存
          </button>{' '}
          <button type="button" className="btn-secondary" onClick={handleCancelEdit} disabled={saving}>
            取消
          </button>
        </form>
      )}

      {rowError && <p role="alert">{rowError}</p>}

      {isLoading ? (
        <p className="empty">載入中...</p>
      ) : (
        <div className="table-wrap">
          <table className="expense-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>說明</th>
                <th className="num">金額</th>
                <th>支付帳戶</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {expenses?.map((expense) => (
                <tr key={expense.expenseId}>
                  <td className="col-date">
                    <div>{new Date(expense.occurredAt).toLocaleDateString('zh-TW')}</div>
                    <div>
                      {new Date(expense.occurredAt).toLocaleTimeString('zh-TW', { hourCycle: 'h23', hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </div>
                  </td>
                  <td className="col-note">{expense.note}</td>
                  <td className={`num col-amount ${expense.amount > 0 ? 'positive' : expense.amount < 0 ? 'negative' : ''}`}>
                    {expense.amount}
                  </td>
                  <td className="col-account">
                    <span className="badge">{expense.paymentAccountName}</span>
                  </td>
                  <td className="col-actions">
                    {expense.locked && editing?.expenseId !== expense.expenseId && (
                      <span className="badge">編輯中</span>
                    )}{' '}
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => handleStartEdit(expense)}
                      disabled={!!editing}
                      aria-label={`編輯 ${expense.note}`}
                    >
                      編輯
                    </button>{' '}
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => handleDelete(expense)}
                      disabled={!!editing}
                      aria-label={`刪除 ${expense.note}`}
                    >
                      刪除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {expensePage && (
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
            第 {page + 1} / {Math.max(totalPages, 1)} 頁（共 {expensePage.totalElements} 筆）
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
