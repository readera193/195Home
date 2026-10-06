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
  email: string;
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

export function leaveGroup(familyGroupId: number, memberId: number): Promise<LeaveResponse> {
  return apiClient.post(`/families/${familyGroupId}/members/${memberId}/leave`).then((res) => res.data);
}

export function kickMember(familyGroupId: number, memberId: number): Promise<{ memberId: number; status: string }> {
  return apiClient.post(`/families/${familyGroupId}/members/${memberId}/kick`).then((res) => res.data);
}

export function restoreMemberEligibility(
  familyGroupId: number,
  memberId: number,
): Promise<{ memberId: number; status: string }> {
  return apiClient
    .post(`/families/${familyGroupId}/members/${memberId}/restore-eligibility`)
    .then((res) => res.data);
}

export interface GenerateCodeResponse {
  code: string;
  expiresAt: string;
}

export function generateLineBindingCode(memberId: number): Promise<GenerateCodeResponse> {
  return apiClient.post(`/families/members/${memberId}/line-binding-codes`).then((res) => res.data);
}
