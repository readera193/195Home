import apiClient from './apiClient';

export interface LoginResponse {
  token: string;
  userId: number;
  displayName: string;
  expiresAt: string;
}

export interface AuthConfig {
  demoLoginEnabled: boolean;
}

// code 為 LINE 授權碼
export function loginWithLine(code: string): Promise<LoginResponse> {
  return apiClient.post('/auth/line', { code }).then((res) => res.data);
}

// Demo 體驗：免 LINE，後端建立隨機帳號（需後端開啟 DEMO_LOGIN_ENABLED）
export function loginAsDemo(): Promise<LoginResponse> {
  return apiClient.post('/auth/demo').then((res) => res.data);
}

export function getAuthConfig(): Promise<AuthConfig> {
  return apiClient.get('/auth/config').then((res) => res.data);
}
