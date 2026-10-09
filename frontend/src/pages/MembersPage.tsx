import React from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getMembers, getMyFamily, kickMember, leaveGroup, restoreMemberEligibility } from '../services/familyApi';

export default function MembersPage() {
  const queryClient = useQueryClient();
  const { data: myFamily } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const { data: members, isLoading } = useQuery({
    queryKey: ['members', familyGroupId],
    queryFn: () => getMembers(familyGroupId!),
    enabled: !!familyGroupId,
  });

  async function handleKick(memberId: number) {
    if (!familyGroupId) return;
    await kickMember(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  /**
   * 復原被管理者移出的成員（FR-030）。
   *
   * 呼叫 restore-eligibility 端點，將該成員狀態由「已移出」(REMOVED) 改回「已離開」(LEFT)。
   * 這個動作不會讓成員直接回到群組，只是解除「不可用邀請碼加入」的限制；
   * 該成員之後仍須自行輸入群組邀請碼，才會回到「在職」。
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
    await leaveGroup(familyGroupId, memberId);
    queryClient.invalidateQueries({ queryKey: ['myFamily'] });
    queryClient.invalidateQueries({ queryKey: ['members', familyGroupId] });
  }

  if (!familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }
  if (isLoading) {
    return <p className="empty">載入中...</p>;
  }

  const isAdmin = myFamily?.role === 'ADMIN';

  return (
    <div>
      <div className="page-header">
        <h1>成員列表</h1>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>角色</th>
              <th>狀態</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {members?.map((member) => (
              <tr key={member.familyMemberId}>
                <td>{member.email}</td>
                <td>
                  <span className={`badge ${member.role === 'ADMIN' ? 'badge-primary' : ''}`}>
                    {member.role === 'ADMIN' ? '管理者' : '一般成員'}
                  </span>
                </td>
                <td>
                  <span
                    className={`badge ${
                      member.status === 'ACTIVE' ? 'badge-success' : member.status === 'REMOVED' ? 'badge-danger' : ''
                    }`}
                  >
                    {member.status === 'ACTIVE' ? '在職' : member.status === 'REMOVED' ? '已移出' : '已離開'}
                  </span>
                </td>
                <td>
                  <div className="actions">
                    {member.status === 'ACTIVE' && (
                      <>
                        <button className="btn-secondary btn-sm" onClick={() => handleLeave(member.familyMemberId)}>
                          離開群組
                        </button>
                        {isAdmin && member.role !== 'ADMIN' && (
                          <button className="btn-danger btn-sm" onClick={() => handleKick(member.familyMemberId)}>
                            移出
                          </button>
                        )}
                      </>
                    )}
                    {isAdmin && member.status === 'REMOVED' && (
                      <button className="btn-secondary btn-sm" onClick={() => handleRestore(member.familyMemberId)}>
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
    </div>
  );
}
