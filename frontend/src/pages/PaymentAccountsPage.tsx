import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { createAccount, disableAccount, listAccounts } from '../services/expenseApi';

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

  async function handleDisable(accountId: number) {
    await disableAccount(accountId);
    queryClient.invalidateQueries({ queryKey: ['accounts', familyGroupId] });
  }

  if (!familyGroupId) {
    return <p>您尚未加入任何家庭群組</p>;
  }

  return (
    <div>
      <h1>支付帳戶</h1>
      {error && <p role="alert">{error}</p>}
      <form onSubmit={handleCreate}>
        <label htmlFor="accountName">帳戶名稱（例如現金、銀行帳戶、信用卡）</label>
        <input id="accountName" value={name} onChange={(e) => setName(e.target.value)} required />
        <button type="submit">建立</button>
      </form>

      {isLoading ? (
        <p>載入中...</p>
      ) : (
        <ul>
          {accounts?.map((account) => (
            <li key={account.accountId}>
              {account.name}（{account.status === 'ACTIVE' ? '啟用中' : '已停用'}）
              {account.status === 'ACTIVE' && <button onClick={() => handleDisable(account.accountId)}>停用</button>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
