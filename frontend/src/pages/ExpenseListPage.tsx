import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMembers, getMyFamily } from '../services/familyApi';
import {
  deleteExpense,
  listAccounts,
  listExpenses,
  lockExpense,
  unlockExpense,
  type ExpenseResponse,
} from '../services/expenseApi';
import { getErrorMessage } from '../utils/apiError';
import { monthBoundary } from '../utils/expenseValidation';
import { confirmDialog, notifyError } from '../utils/notify';
import CreateExpenseDialog from '../components/expense/CreateExpenseDialog';
import EditExpenseForm from '../components/expense/EditExpenseForm';
import ExpenseFilters, { type ExpenseFilterValues } from '../components/expense/ExpenseFilters';
import ExpenseTable from '../components/expense/ExpenseTable';

const PAGE_SIZE = 20;

export default function ExpenseListPage() {
  const familyQuery = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const myFamily = familyQuery.data;
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

  const [filters, setFilters] = useState<ExpenseFilterValues>(() => ({
    paymentAccountId: '',
    authorMemberId: '',
    startDate: monthBoundary(0),
    endDate: monthBoundary(1),
  }));
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  // 編輯／刪除（FR-019、FR-027）：先取得鎖才能操作，取消或完成時釋放
  const [editing, setEditing] = useState<ExpenseResponse | null>(null);

  function refreshExpenses() {
    return queryClient.invalidateQueries({ queryKey: ['expenses'] });
  }

  function handleFilterChange(patch: Partial<ExpenseFilterValues>) {
    setFilters((current) => ({ ...current, ...patch }));
    setPage(0);
  }

  async function handleStartEdit(expense: ExpenseResponse) {
    if (!familyGroupId) return;
    try {
      await lockExpense(expense.expenseId, familyGroupId);
    } catch (err) {
      notifyError(getErrorMessage(err, '無法編輯此紀錄'));
      await refreshExpenses();
      return;
    }
    setEditing(expense);
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
    await refreshExpenses();
  }

  async function handleSavedEdit() {
    // 更新成功時後端已一併釋放鎖
    setEditing(null);
    await refreshExpenses();
  }

  async function handleCreated() {
    setPage(0);
    setShowCreate(false);
    await refreshExpenses();
  }

  async function handleDelete(expense: ExpenseResponse) {
    if (!familyGroupId) return;
    try {
      await lockExpense(expense.expenseId, familyGroupId);
    } catch (err) {
      notifyError(getErrorMessage(err, '無法刪除此紀錄'));
      await refreshExpenses();
      return;
    }
    if (!(await confirmDialog({ title: `確定要刪除「${expense.note}」嗎？`, confirmText: '刪除', danger: true }))) {
      try {
        await unlockExpense(expense.expenseId);
      } catch {
        // 同上：鎖會自動逾時
      }
      await refreshExpenses();
      return;
    }
    try {
      await deleteExpense(expense.expenseId, familyGroupId);
    } catch (err) {
      notifyError(getErrorMessage(err, '刪除支出失敗'));
    }
    await refreshExpenses();
  }

  const { data: expensePage, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: [
      'expenses',
      familyGroupId,
      filters.paymentAccountId,
      filters.authorMemberId,
      filters.startDate,
      filters.endDate,
      page,
    ],
    queryFn: () =>
      listExpenses({
        familyGroupId: familyGroupId!,
        paymentAccountId: filters.paymentAccountId ? Number(filters.paymentAccountId) : undefined,
        authorMemberId: filters.authorMemberId ? Number(filters.authorMemberId) : undefined,
        startDate: filters.startDate || undefined,
        endDate: filters.endDate || undefined,
        page,
        size: PAGE_SIZE,
      }),
    enabled: !!familyGroupId,
    placeholderData: keepPreviousData,
  });
  const totalPages = expensePage?.totalPages ?? 0;

  if (familyQuery.isLoading) return <p role="status">載入中...</p>;
  if (familyQuery.isError) return (
    <div className="card" role="alert">
      <p>無法載入家庭資料，請稍後再試。</p>
      <button type="button" onClick={() => void familyQuery.refetch()}>重新載入</button>
    </div>
  );

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
        <CreateExpenseDialog
          familyGroupId={familyGroupId}
          activeAccounts={activeAccounts}
          onClose={() => setShowCreate(false)}
          onCreated={handleCreated}
        />
      )}

      <ExpenseFilters values={filters} accounts={accounts} members={members} onChange={handleFilterChange} />

      {editing && (
        <EditExpenseForm
          key={editing.expenseId}
          familyGroupId={familyGroupId}
          expense={editing}
          accounts={accounts}
          onCancel={handleCancelEdit}
          onSaved={handleSavedEdit}
        />
      )}

      {isLoading ? (
        <p className="empty">載入中...</p>
      ) : isError ? (
        <div className="card" role="alert">
          <p>無法載入支出紀錄，請重新載入。</p>
          <button type="button" disabled={isFetching} onClick={() => void refetch()}>重新載入</button>
        </div>
      ) : (
        <ExpenseTable
          expenses={expensePage?.items}
          editingId={editing?.expenseId}
          onEdit={handleStartEdit}
          onDelete={handleDelete}
        />
      )}

      {expensePage && !isError && (
        <nav className="pagination" aria-label="分頁">
          <button type="button" className="btn-secondary" onClick={() => setPage((p) => p - 1)} disabled={isFetching || page === 0}>
            上一頁
          </button>
          <span>
            第 {page + 1} / {Math.max(totalPages, 1)} 頁（共 {expensePage.totalElements} 筆）
          </span>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setPage((p) => p + 1)}
            disabled={isFetching || page + 1 >= totalPages}
          >
            下一頁
          </button>
        </nav>
      )}
    </div>
  );
}
