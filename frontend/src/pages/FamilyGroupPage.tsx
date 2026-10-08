import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { createFamilyGroup, getMyFamily, joinFamilyGroup } from '../services/familyApi';
import { getErrorMessage } from '../utils/apiError';

export default function FamilyGroupPage() {
  const queryClient = useQueryClient();
  const { data: myFamily, isLoading } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });

  const [groupName, setGroupName] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await createFamilyGroup(groupName);
      queryClient.invalidateQueries({ queryKey: ['myFamily'] });
    } catch (err) {
      setError(getErrorMessage(err, '建立群組失敗'));
    }
  }

  async function handleJoin(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await joinFamilyGroup(inviteCode);
      queryClient.invalidateQueries({ queryKey: ['myFamily'] });
    } catch (err) {
      setError(getErrorMessage(err, '加入群組失敗'));
    }
  }

  if (isLoading) {
    return <p>載入中...</p>;
  }

  if (myFamily?.familyGroupId) {
    return (
      <div>
        <h1>我的家庭群組</h1>
        <p>群組名稱：{myFamily.name}</p>
        <p>我的角色：{myFamily.role === 'ADMIN' ? '管理者' : '一般成員'}</p>
        <p>
          邀請碼：<strong>{myFamily.inviteCode}</strong>（可重複使用、無時限，分享給家人即可加入）
        </p>
        <p>
          <Link to="/members">查看成員列表</Link>
        </p>
      </div>
    );
  }

  return (
    <div>
      <h1>建立或加入家庭群組</h1>
      {error && <p role="alert">{error}</p>}

      <section>
        <h2>建立新群組</h2>
        <form onSubmit={handleCreate}>
          <label htmlFor="groupName">群組名稱</label>
          <input id="groupName" value={groupName} onChange={(e) => setGroupName(e.target.value)} required />
          <button type="submit">建立</button>
        </form>
      </section>

      <section>
        <h2>使用邀請碼加入</h2>
        <form onSubmit={handleJoin}>
          <label htmlFor="inviteCode">邀請碼</label>
          <input id="inviteCode" value={inviteCode} onChange={(e) => setInviteCode(e.target.value)} required />
          <button type="submit">加入</button>
        </form>
      </section>
    </div>
  );
}
