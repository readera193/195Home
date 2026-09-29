import apiClient from './apiClient';

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
  expiresAt: string;
}

export function register(request: RegisterRequest): Promise<{ userId: number; email: string }> {
  return apiClient.post('/users/register', request).then((res) => res.data);
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiClient.post('/users/login', request).then((res) => res.data);
}
