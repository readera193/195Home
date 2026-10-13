import apiClient from './apiClient';

export interface LoginResponse {
  token: string;
  userId: number;
  displayName: string;
  expiresAt: string;
}

// dev code 即暱稱（後端 DevLineLoginClient）；prod 為 LINE 授權碼
export function loginWithLine(code: string): Promise<LoginResponse> {
  return apiClient.post('/auth/line', { code }).then((res) => res.data);
}
