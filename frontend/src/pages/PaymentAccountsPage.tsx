import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import {
  createAccount,
  deleteAccount,
  disableAccount,
  enableAccount,
  listAccounts,
  renameAccount,
} from '../services/expenseApi';
import { getErrorMessage } from '../utils/apiError';

export default function PaymentAccountsPage() {
  const queryClient = useQueryClient();
  const { data: myFamily } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts', familyGroupId],
    queryFn: () => listAccounts(familyGroupId!),
    enabled: !!familyGroupId,
  });

  const [name, setName] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [showDisabled, setShowDisabled] = useState(false);
  const visibleAccounts = showDisabled ? accounts : accounts?.filter((account) => account.status === 'ACTIVE');
  const [error, setError] = useState<string | null>(null);

  function openCreate() {
    setError(null);
    setName('');
    setShowCreate(true);
  }

  function closeCreate() {
    setShowCreate(false);
    setError(null);
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!familyGroupId) return;
    setError(null);
    try {
      await createAccount(familyGroupId, name);
      setName('');
      setShowCreate(false);
      queryClient.invalidateQueries({ queryKey: ['accounts', familyGroupId] });
    } catch (err) {
      setError(getErrorMessage(err, '建立支付帳戶失敗'));
    }
  }

  async function runAdminAction(action: () => Promise<unknown>, fallbackMessage: string) {
    setError(null);
    try {
      await action();
      queryClient.invalidateQueries({ queryKey: ['accounts', familyGroupId] });
    } catch (err) {
      setError(getErrorMessage(err, fallbackMessage));
    }
  }

  function handleDisable(accountId: number) {
    if (!window.confirm('確定要停用此支付帳戶嗎？停用後將無法用於新的支出。')) return;
    return runAdminAction(() => disableAccount(accountId), '停用支付帳戶失敗');
  }

  function handleEnable(accountId: number) {
    return runAdminAction(() => enableAccount(accountId), '啟用支付帳戶失敗');
  }

  function handleRename(accountId: number, currentName: string) {
    const newName = window.prompt('新的帳戶名稱', currentName)?.trim();
    if (!newName || newName === currentName) return;
    return runAdminAction(() => renameAccount(accountId, newName), '修改支付帳戶失敗');
  }

  function handleDelete(accountId: number) {
    if (!window.confirm('確定要刪除此支付帳戶嗎？（僅限從未被使用過的帳戶）')) return;
    return runAdminAction(() => deleteAccount(accountId), '刪除支付帳戶失敗');
  }

  if (!familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }

  // FR-003、FR-022：僅群組管理者可新增、修改、停用、刪除支付帳戶
  const isAdmin = myFamily?.role === 'ADMIN';

  return (
    <div className="container-narrow">
      <div className="page-header page-header--row">
        <h1>支付帳戶</h1>
        {isAdmin && (
          <button type="button" onClick={openCreate}>
            新增帳戶
          </button>
        )}
      </div>
      {error && !showCreate && <p role="alert">{error}</p>}
      {!isAdmin && <p className="notice notice-info">僅群組管理者可管理支付帳戶</p>}

      {showCreate && (
        <div className="modal-backdrop" onClick={closeCreate}>
          <form
            className="card modal"
            role="dialog"
            aria-modal="true"
            aria-label="新增帳戶"
            onClick={(e) => e.stopPropagation()}
            onSubmit={handleCreate}
          >
            <h2>新增帳戶</h2>
            {error && <p role="alert">{error}</p>}
            <div className="field">
              <label htmlFor="accountName">帳戶名稱</label>
              <input id="accountName" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
            </div>
            <button type="submit" className="btn-block">
              新增
            </button>
            <button type="button" className="btn-secondary btn-block" onClick={closeCreate}>
              取消
            </button>
          </form>
        </div>
      )}

      <label className="checkbox-label">
        <input type="checkbox" checked={showDisabled} onChange={(e) => setShowDisabled(e.target.checked)} />
        顯示已停用帳戶
      </label>

      {isLoading ? (
        <p className="empty">載入中...</p>
      ) : (
        <ul className="card list">
          {visibleAccounts?.map((account) => (
            <li key={account.accountId} className="list-item">
              <span className="list-item-main">
                {account.name}
                <span className={`badge ${account.status === 'ACTIVE' ? 'badge-success' : ''}`}>
                  {account.status === 'ACTIVE' ? '啟用中' : '已停用'}
                </span>
              </span>
              {isAdmin && (
                <span className="actions">
                  <button className="btn-secondary btn-sm" onClick={() => handleRename(account.accountId, account.name)}>
                    改名
                  </button>
                  {account.status === 'ACTIVE' ? (
                    <button className="btn-secondary btn-sm" onClick={() => handleDisable(account.accountId)}>
                      停用
                    </button>
                  ) : (
                    <button className="btn-secondary btn-sm" onClick={() => handleEnable(account.accountId)}>
                      啟用
                    </button>
                  )}
                  {account.deletable && (
                    <button className="btn-danger btn-sm" onClick={() => handleDelete(account.accountId)}>
                      刪除
                    </button>
                  )}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
