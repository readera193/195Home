import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createFamilyGroup,
  getMembers,
  getMyFamily,
  joinFamilyGroup,
  kickMember,
  leaveGroup,
  restoreMemberEligibility,
} from '../services/familyApi';
import { getErrorMessage } from '../utils/apiError';
import { confirmDialog, notifyError } from '../utils/notify';
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
    if (!(await confirmDialog({ title: '確定要將此成員移出群組嗎？', text: '被移出的成員無法再用邀請碼加入，除非管理者復原其資格。', confirmText: '移出', danger: true }))) return;
    await kickMember(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  /**
   * 復原被管理者移出的成員（FR-030）。
   *
   * 呼叫 restore-eligibility 端點，將該成員狀態由「已移出」(REMOVED) 改回「已離開」(LEFT)。
   * 這個動作不會讓成員直接回到群組，只是解除「不可用邀請碼加入」的限制；
   * 該成員之後仍須自行輸入群組邀請碼，才會回到「正常」。
   * 僅管理者可操作（畫面上只對管理者顯示按鈕，後端亦會驗證，非管理者回傳 403）。
   * 完成後重新載入成員列表，使該成員的狀態顯示為「已離開」。
   */
  async function handleRestore(memberId: number) {
    if (!familyGroupId) return;
    await restoreMemberEligibility(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  async function handleLeave(memberId: number) {
    if (!familyGroupId) return;
    if (!(await confirmDialog({ title: '確定要離開此群組嗎？', text: '若您是最後一位成員，群組將自動解散。', confirmText: '離開', danger: true }))) return;
    await leaveGroup(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['myFamily'] });
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  if (isLoading) {
    return <p className="empty">載入中...</p>;
  }

  if (myFamily?.familyGroupId) {
    const isAdmin = myFamily.role === 'ADMIN';
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
                {members?.map((member) => (
                  <tr key={member.familyMemberId}>
                    <td>{member.displayName}</td>
                    <td>
                      <span className={`badge ${member.role === 'ADMIN' ? 'badge-primary' : ''}`}>
                        {member.role === 'ADMIN' ? '管理者' : '一般成員'}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`badge ${
                          member.status === 'ACTIVE'
                            ? 'badge-success'
                            : member.status === 'REMOVED'
                              ? 'badge-danger'
                              : ''
                        }`}
                      >
                        {member.status === 'ACTIVE' ? '正常' : member.status === 'REMOVED' ? '已移出' : '已離開'}
                      </span>
                    </td>
                    <td>
                      <div className="actions">
                        {member.status === 'ACTIVE' && member.familyMemberId === myFamily.familyMemberId && (
                          <button className="btn-secondary btn-sm" onClick={() => handleLeave(member.familyMemberId)}>
                            離開群組
                          </button>
                        )}
                        {member.status === 'ACTIVE' && isAdmin && member.familyMemberId !== myFamily.familyMemberId && (
                          <button className="btn-danger btn-sm" onClick={() => handleKick(member.familyMemberId)}>
                            移出
                          </button>
                        )}
                        {isAdmin && member.status === 'REMOVED' && (
                          <button
                            className="btn-secondary btn-sm"
                            onClick={() => handleRestore(member.familyMemberId)}
                          >
                            復原
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
