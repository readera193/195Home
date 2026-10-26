import apiClient from './apiClient';

export interface FamilyGroupResponse {
  familyGroupId: number;
  name: string;
  inviteCode: string;
  role: string;
}

export interface JoinFamilyGroupResponse {
  familyGroupId: number;
  name: string | null;
  role: string;
  status: string;
}

export interface MemberView {
  familyMemberId: number;
  userId: number;
  displayName: string;
  status: 'ACTIVE' | 'LEFT' | 'REMOVED';
  role: 'ADMIN' | 'MEMBER';
  joinedAt: string;
  leftAt: string | null;
}

export interface LeaveResponse {
  status: string;
  familyGroupStatus: string;
  newAdminMemberId: number | null;
}

export interface MyFamilyResponse {
  familyGroupId: number | null;
  name: string | null;
  status: string | null;
  role: string | null;
  inviteCode: string | null;
  familyMemberId: number | null;
}

export function getMyFamily(): Promise<MyFamilyResponse> {
  return apiClient.get('/families/me').then((res) => res.data);
}

export function createFamilyGroup(name: string): Promise<FamilyGroupResponse> {
  return apiClient.post('/families', { name }).then((res) => res.data);
}

export function joinFamilyGroup(inviteCode: string): Promise<JoinFamilyGroupResponse> {
  return apiClient.post('/families/join', { inviteCode }).then((res) => res.data);
}

export function getMembers(familyGroupId: number, includeLeft = true): Promise<MemberView[]> {
  return apiClient
    .get(`/families/${familyGroupId}/members`, { params: { includeLeft } })
    .then((res) => res.data);
}

/** 管理者離開仍有其他在職成員的群組時，須帶入 newAdminMemberId 指定接任者（FR-029）。 */
export function leaveGroup(
  familyGroupId: number,
  memberId: number,
  newAdminMemberId?: number,
): Promise<LeaveResponse> {
  return apiClient
    .post(`/families/${familyGroupId}/members/${memberId}/leave`, { newAdminMemberId })
    .then((res) => res.data);
}

/** 管理者直接指定另一位在職成員為管理者（FR-032）。 */
export function transferAdmin(familyGroupId: number, newAdminMemberId: number): Promise<MemberView> {
  return apiClient
    .post(`/families/${familyGroupId}/transfer-admin`, { newAdminMemberId })
    .then((res) => res.data);
}

export function kickMember(familyGroupId: number, memberId: number): Promise<{ memberId: number; status: string }> {
  return apiClient.post(`/families/${familyGroupId}/members/${memberId}/kick`).then((res) => res.data);
}

export function renameMember(familyGroupId: number, memberId: number, displayName: string): Promise<MemberView> {
  return apiClient
    .put(`/families/${familyGroupId}/members/${memberId}/display-name`, { displayName })
    .then((res) => res.data);
}

export function restoreMemberEligibility(
  familyGroupId: number,
  memberId: number,
): Promise<{ memberId: number; status: string }> {
  return apiClient
    .post(`/families/${familyGroupId}/members/${memberId}/restore-eligibility`)
    .then((res) => res.data);
}
