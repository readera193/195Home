import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { createAccount, deleteAccount, disableAccount, listAccounts, renameAccount } from '../services/expenseApi';
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
  const [error, setError] = useState<string | null>(null);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!familyGroupId) return;
    setError(null);
    try {
      await createAccount(familyGroupId, name);
      setName('');
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
    return runAdminAction(() => disableAccount(accountId), '停用支付帳戶失敗');
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
      <div className="page-header">
        <h1>支付帳戶</h1>
      </div>
      {error && <p role="alert">{error}</p>}
      {isAdmin ? (
        <form className="card inline-form" onSubmit={handleCreate}>
          <div className="field">
            <label htmlFor="accountName">帳戶名稱</label>
            <input id="accountName" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <button type="submit">建立</button>
        </form>
      ) : (
        <p className="notice notice-info">僅群組管理者可管理支付帳戶</p>
      )}

      {isLoading ? (
        <p className="empty">載入中...</p>
      ) : (
        <ul className="card list">
          {accounts?.map((account) => (
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
                  {account.status === 'ACTIVE' && (
                    <button className="btn-secondary btn-sm" onClick={() => handleDisable(account.accountId)}>
                      停用
                    </button>
                  )}
                  <button className="btn-danger btn-sm" onClick={() => handleDelete(account.accountId)}>
                    刪除
                  </button>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
