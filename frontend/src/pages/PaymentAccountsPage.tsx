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
import { confirmDialog, notifyError, promptText } from '../utils/notify';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import { SkeletonLines } from '../components/Skeleton';

export default function PaymentAccountsPage() {
  const queryClient = useQueryClient();
  const { data: myFamily, isLoading: familyLoading } = useQuery({
    queryKey: ['myFamily'],
    queryFn: getMyFamily,
  });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts', familyGroupId],
    queryFn: () => listAccounts(familyGroupId!),
    enabled: !!familyGroupId,
  });

  const [name, setName] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [showDisabled, setShowDisabled] = useState(false);
  const visibleAccounts = showDisabled
    ? accounts
    : accounts?.filter((account) => account.status === 'ACTIVE');

  function openCreate() {
    setName('');
    setShowCreate(true);
  }

  function closeCreate() {
    setShowCreate(false);
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!familyGroupId) return;
    try {
      await createAccount(familyGroupId, name);
      setName('');
      setShowCreate(false);
      queryClient.invalidateQueries({ queryKey: ['accounts', familyGroupId] });
    } catch (err) {
      notifyError(getErrorMessage(err, '建立支付帳戶失敗'));
    }
  }

  async function runAdminAction(action: () => Promise<unknown>, fallbackMessage: string) {
    try {
      await action();
      queryClient.invalidateQueries({ queryKey: ['accounts', familyGroupId] });
    } catch (err) {
      notifyError(getErrorMessage(err, fallbackMessage));
    }
  }

  async function handleDisable(accountId: number) {
    if (
      !(await confirmDialog({
        title: '確定要停用此支付帳戶嗎？',
        text: '停用後將無法用於新的支出。',
        confirmText: '停用',
      }))
    )
      return;
    return runAdminAction(() => disableAccount(accountId), '停用支付帳戶失敗');
  }

  function handleEnable(accountId: number) {
    return runAdminAction(() => enableAccount(accountId), '啟用支付帳戶失敗');
  }

  async function handleRename(accountId: number, currentName: string) {
    const newName = (await promptText('新的帳戶名稱', currentName))?.trim();
    if (!newName || newName === currentName) return;
    return runAdminAction(() => renameAccount(accountId, newName), '修改支付帳戶失敗');
  }

  async function handleDelete(accountId: number) {
    if (
      !(await confirmDialog({
        title: '確定要刪除此支付帳戶嗎？',
        text: '僅限從未被使用過的帳戶。',
        confirmText: '刪除',
        danger: true,
      }))
    )
      return;
    return runAdminAction(() => deleteAccount(accountId), '刪除支付帳戶失敗');
  }

  if (familyLoading) return <SkeletonLines count={4} />;

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
    <div className="container-narrow page-compact">
      <div className="page-header page-header--row">
        <h1>支付帳戶</h1>
        {isAdmin && (
          <button type="button" onClick={openCreate}>
            新增帳戶
          </button>
        )}
      </div>
      {!isAdmin && <p className="notice notice-info">僅群組管理者可管理支付帳戶</p>}

      {showCreate && (
        <Modal label="新增帳戶" onClose={closeCreate} onSubmit={handleCreate}>
          <h2>新增帳戶</h2>
          <div className="field">
            <label htmlFor="accountName">帳戶名稱</label>
            <input
              id="accountName"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
            />
          </div>
          <button type="submit" className="btn-block">
            新增
          </button>
          <button type="button" className="btn-secondary btn-block" onClick={closeCreate}>
            取消
          </button>
        </Modal>
      )}

      <label className="checkbox-label">
        <input
          type="checkbox"
          checked={showDisabled}
          onChange={(e) => setShowDisabled(e.target.checked)}
        />
        顯示已停用帳戶
      </label>

      {isLoading ? (
        <SkeletonLines count={3} />
      ) : visibleAccounts?.length === 0 ? (
        <EmptyState
          title="還沒有支付帳戶"
          hint={isAdmin ? '建立第一個帳戶後，就能開始記錄支出。' : '請群組管理者先建立支付帳戶。'}
          action={
            isAdmin && (
              <button type="button" onClick={openCreate}>
                建立第一個帳戶
              </button>
            )
          }
        />
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
                  <button
                    className="btn-secondary btn-sm"
                    onClick={() => handleRename(account.accountId, account.name)}
                  >
                    編輯
                  </button>
                  {account.status === 'ACTIVE' ? (
                    <button
                      className="btn-secondary btn-sm"
                      onClick={() => handleDisable(account.accountId)}
                    >
                      停用
                    </button>
                  ) : (
                    <button
                      className="btn-secondary btn-sm"
                      onClick={() => handleEnable(account.accountId)}
                    >
                      啟用
                    </button>
                  )}
                  {account.deletable && (
                    <button
                      className="btn-danger btn-sm"
                      onClick={() => handleDelete(account.accountId)}
                    >
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
