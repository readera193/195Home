import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createFamilyGroup,
  getMembers,
  getMyFamily,
  joinFamilyGroup,
  kickMember,
  leaveGroup,
  renameMember,
  restoreMemberEligibility,
  transferAdmin,
} from '../services/familyApi';
import { getErrorMessage } from '../utils/apiError';
import { confirmDialog, notifyError, promptText, selectOption } from '../utils/notify';
import InfoTip from '../components/InfoTip';

export default function FamilyGroupPage() {
  const queryClient = useQueryClient();
  const { data: myFamily, isLoading } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const { data: members, isLoading: membersLoading } = useQuery({
    queryKey: ['members', familyGroupId],
    queryFn: () => getMembers(familyGroupId!),
    enabled: !!familyGroupId,
  });

  const [groupName, setGroupName] = useState('');
  const [inviteCode, setInviteCode] = useState('');

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    try {
      await createFamilyGroup(groupName);
      queryClient.invalidateQueries({ queryKey: ['myFamily'] });
    } catch (err) {
      notifyError(getErrorMessage(err, '建立群組失敗'));
    }
  }

  async function handleJoin(e: React.FormEvent) {
    e.preventDefault();
    try {
      await joinFamilyGroup(inviteCode);
      queryClient.invalidateQueries({ queryKey: ['myFamily'] });
    } catch (err) {
      notifyError(getErrorMessage(err, '加入群組失敗'));
    }
  }

  async function handleKick(memberId: number) {
    if (!familyGroupId) return;
    if (!(await confirmDialog({ title: '確定要將此成員移出群組嗎？', text: '移出後對方將被封鎖，無法再加入，需由管理員解除封鎖才能重新加入。', confirmText: '移出', danger: true }))) return;
    await kickMember(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  /**
   * 解除對被管理者移出成員的封鎖（FR-030）。
   *
   * 呼叫 restore-eligibility 端點，將該成員狀態由「已封鎖」(REMOVED) 改回「已退出」(LEFT)。
   * 這個動作不會讓成員直接回到群組，只是解除「不可用邀請碼加入」的限制；
   * 該成員之後仍須自行輸入群組邀請碼，才會回到「正常」。
   * 僅管理者可操作（畫面上只對管理者顯示按鈕，後端亦會驗證，非管理者回傳 403）。
   * 完成後重新載入成員列表，使該成員的狀態顯示為「已退出」。
   */
  async function handleRestore(memberId: number, name: string) {
    if (!familyGroupId) return;
    if (!(await confirmDialog({ title: `確定要解除「${name}」的封鎖嗎？`, text: `解除封鎖後，${name}可以再次使用邀請碼加入群組，不會自動加回成員。`, confirmText: '解除封鎖' }))) return;
    await restoreMemberEligibility(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  // 一般成員可編輯自己的名稱；管理者可編輯所有成員的名稱（後端亦會驗證）
  async function handleRename(memberId: number, currentName: string) {
    if (!familyGroupId) return;
    const name = (await promptText('編輯顯示名稱', currentName))?.trim();
    if (!name || name === currentName) return;
    try {
      await renameMember(familyGroupId, memberId, name);
      queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
    } catch (err) {
      notifyError(getErrorMessage(err, '編輯名稱失敗'));
    }
  }

  // 管理者離開仍有其他在職成員的群組時，必須先指定下一位管理者（FR-029）
  async function handleLeave(memberId: number, isAdmin: boolean) {
    if (!familyGroupId) return;
    const others = (members ?? []).filter((m) => m.status === 'ACTIVE' && m.familyMemberId !== memberId);
    try {
      if (isAdmin && others.length > 0) {
        const newAdminMemberId = await selectOption({
          title: '離開前請先指定下一位管理者',
          text: '指定的成員將在您離開後成為群組管理者。',
          choices: others.map((m) => ({ value: m.familyMemberId, label: m.displayName })),
          placeholder: '選擇下一位管理者',
          confirmText: '指定並離開',
          danger: true,
        });
        if (newAdminMemberId === undefined) return;
        await leaveGroup(familyGroupId, memberId, newAdminMemberId);
      } else {
        if (!(await confirmDialog({ title: '確定要離開此群組嗎？', text: '若您是最後一位成員，群組將自動解散。', confirmText: '離開', danger: true }))) return;
        await leaveGroup(familyGroupId, memberId);
      }
      queryClient.invalidateQueries({ queryKey: ['myFamily'] });
      queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
    } catch (err) {
      notifyError(getErrorMessage(err, '離開群組失敗'));
    }
  }

  // 管理者直接把管理者身分交給另一位在職成員，自己降為一般成員（FR-032）
  async function handleTransferAdmin(memberId: number, name: string) {
    if (!familyGroupId) return;
    if (!(await confirmDialog({ title: `確定要將管理者轉給「${name}」嗎？`, text: '轉移後您將變成一般成員，失去管理權限。', confirmText: '轉移', danger: true }))) return;
    try {
      await transferAdmin(familyGroupId, memberId);
      queryClient.invalidateQueries({ queryKey: ['myFamily'] });
      queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
    } catch (err) {
      notifyError(getErrorMessage(err, '轉移管理者失敗'));
    }
  }

  if (isLoading) {
    return <p className="empty">載入中...</p>;
  }

  if (myFamily?.familyGroupId) {
    const isAdmin = myFamily.role === 'ADMIN';
    const activeMembers = (members ?? []).filter((m) => m.status === 'ACTIVE');
    // 已封鎖排在已退出之前，方便管理者找到可解除封鎖的成員
    const inactiveMembers = (members ?? [])
      .filter((m) => m.status !== 'ACTIVE')
      .sort((a, b) => Number(b.status === 'REMOVED') - Number(a.status === 'REMOVED'));

    const renderTable = (list: typeof activeMembers) => (
      <div className="table-wrap">
        <table className="member-table">
          <thead>
            <tr>
              <th>成員</th>
              <th>角色</th>
              <th>狀態</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {list.map((member) => (
              <tr key={member.familyMemberId}>
                <td>{member.displayName}</td>
                <td>
                  {member.status === 'ACTIVE' ? (
                    <span className={`badge ${member.role === 'ADMIN' ? 'badge-primary' : ''}`}>
                      {member.role === 'ADMIN' ? '管理者' : '一般成員'}
                    </span>
                  ) : (
                    // 已退出／已封鎖的成員不再具有角色（FR-033）
                    <span className="muted">—</span>
                  )}
                </td>
                <td>
                  <span
                    className={`badge ${
                      member.status === 'ACTIVE' ? 'badge-success' : member.status === 'REMOVED' ? 'badge-danger' : ''
                    }`}
                  >
                    {member.status === 'ACTIVE' ? '正常' : member.status === 'REMOVED' ? '已封鎖' : '已退出'}
                  </span>
                </td>
                <td>
                  <div className="actions">
                    {(isAdmin || member.familyMemberId === myFamily.familyMemberId) && member.status !== 'REMOVED' && (
                      <button className="btn-secondary btn-sm" onClick={() => handleRename(member.familyMemberId, member.displayName)}>
                        編輯
                      </button>
                    )}
                    {member.status === 'ACTIVE' && member.familyMemberId === myFamily.familyMemberId && (
                      <button className="btn-secondary btn-sm" onClick={() => handleLeave(member.familyMemberId, isAdmin)}>
                        離開群組
                      </button>
                    )}
                    {member.status === 'ACTIVE' && isAdmin && member.familyMemberId !== myFamily.familyMemberId && (
                      <button className="btn-secondary btn-sm" onClick={() => handleTransferAdmin(member.familyMemberId, member.displayName)}>
                        轉移管理員
                      </button>
                    )}
                    {member.status === 'ACTIVE' && isAdmin && member.familyMemberId !== myFamily.familyMemberId && (
                      <button className="btn-danger btn-sm" onClick={() => handleKick(member.familyMemberId)}>
                        移出
                      </button>
                    )}
                    {isAdmin && member.status === 'REMOVED' && (
                      <button className="btn-secondary btn-sm" onClick={() => handleRestore(member.familyMemberId, member.displayName)}>
                        解除封鎖
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );

    return (
      <div className="container-narrow page-medium">
        <div className="page-header">
          <h1>我的家庭群組</h1>
        </div>
        <div className="card">
          <dl className="info-list">
            <dt>群組名稱</dt>
            <dd>{myFamily.name}</dd>
            <dt>我的角色</dt>
            <dd>
              <span className={`badge ${isAdmin ? 'badge-primary' : ''}`}>{isAdmin ? '管理者' : '一般成員'}</span>
            </dd>
            <dt>
              邀請碼 <InfoTip text="可重複使用、無時限，分享給家人即可加入。" />
            </dt>
            <dd>
              <strong className="code-box">{myFamily.inviteCode}</strong>
            </dd>
          </dl>
        </div>

        <h2>成員列表</h2>
        {membersLoading ? (
          <p className="empty">載入中...</p>
        ) : (
          <>
            {renderTable(activeMembers)}
            {inactiveMembers.length > 0 && (
              <details className="inactive-members">
                <summary>已不在群組的成員（{inactiveMembers.length}）</summary>
                <p className="muted hint">已封鎖：被管理員移出，無法再加入；已退出：自行離開，可使用邀請碼重新加入。</p>
                {renderTable(inactiveMembers)}
              </details>
            )}
          </>
        )}
      </div>
    );
  }

  return (
    <div className="container-narrow page-compact">
      <div className="page-header">
        <h1>建立或加入家庭群組</h1>
      </div>

      <section className="card">
        <h2>建立新群組</h2>
        <form className="inline-form" onSubmit={handleCreate}>
          <div className="field">
            <label htmlFor="groupName">群組名稱</label>
            <input id="groupName" value={groupName} onChange={(e) => setGroupName(e.target.value)} required />
          </div>
          <button type="submit">建立</button>
        </form>
      </section>

      <section className="card">
        <h2>使用邀請碼加入</h2>
        <form className="inline-form" onSubmit={handleJoin}>
          <div className="field">
            <label htmlFor="inviteCode">邀請碼</label>
            <input id="inviteCode" value={inviteCode} onChange={(e) => setInviteCode(e.target.value)} required />
          </div>
          <button type="submit">加入</button>
        </form>
      </section>
    </div>
  );
}
