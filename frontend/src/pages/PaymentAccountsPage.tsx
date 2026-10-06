import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { createAccount, deleteAccount, disableAccount, listAccounts, renameAccount } from '../services/expenseApi';

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
    } catch (err: any) {
      setError(err.response?.data?.message ?? '建立支付帳戶失敗');
    }
  }

  async function runAdminAction(action: () => Promise<unknown>, fallbackMessage: string) {
    setError(null);
    try {
      await action();
      queryClient.invalidateQueries({ queryKey: ['accounts', familyGroupId] });
    } catch (err: any) {
      setError(err.response?.data?.message ?? fallbackMessage);
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
    return <p>您尚未加入任何家庭群組</p>;
  }

  // FR-003、FR-022：僅群組管理者可新增、修改、停用、刪除支付帳戶
  const isAdmin = myFamily?.role === 'ADMIN';

  return (
    <div>
      <h1>支付帳戶</h1>
      {error && <p role="alert">{error}</p>}
      {isAdmin ? (
        <form onSubmit={handleCreate}>
          <label htmlFor="accountName">帳戶名稱</label>
          <input id="accountName" value={name} onChange={(e) => setName(e.target.value)} required />
          <button type="submit">建立</button>
        </form>
      ) : (
        <p>僅群組管理者可管理支付帳戶</p>
      )}

      {isLoading ? (
        <p>載入中...</p>
      ) : (
        <ul>
          {accounts?.map((account) => (
            <li key={account.accountId}>
              {account.name}（{account.status === 'ACTIVE' ? '啟用中' : '已停用'}）
              {isAdmin && (
                <>
                  <button onClick={() => handleRename(account.accountId, account.name)}>改名</button>
                  {account.status === 'ACTIVE' && (
                    <button onClick={() => handleDisable(account.accountId)}>停用</button>
                  )}
                  <button onClick={() => handleDelete(account.accountId)}>刪除</button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
