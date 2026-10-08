import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AxiosError, AxiosHeaders } from 'axios';
import LoginPage from '../src/pages/LoginPage';
import * as userApi from '../src/services/userApi';
import { AuthProvider } from '../src/hooks/useAuth';

vi.mock('../src/services/userApi');

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('顯示驗證錯誤訊息，當登入失敗時', async () => {
    // 後端錯誤格式為 RFC 9457 Problem Details：訊息在 detail，錯誤代碼在 code
    const config = { headers: new AxiosHeaders() };
    vi.mocked(userApi.login).mockRejectedValue(
      new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
        data: { status: 401, title: 'Unauthorized', detail: '帳號或密碼錯誤', code: 'INVALID_CREDENTIALS' },
        status: 401,
        statusText: '',
        headers: {},
        config,
      }),
    );

    renderLoginPage();

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@example.com' } });
    fireEvent.change(screen.getByLabelText('密碼'), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByRole('button', { name: '登入' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('帳號或密碼錯誤');
    });
  });

  it('登入成功時呼叫 login API 並帶入輸入的帳密', async () => {
    vi.mocked(userApi.login).mockResolvedValue({
      token: 'jwt-token',
      userId: 1,
      email: 'a@example.com',
      expiresAt: '2026-01-01T00:00:00Z',
    });

    renderLoginPage();

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@example.com' } });
    fireEvent.change(screen.getByLabelText('密碼'), { target: { value: 'correct-password' } });
    fireEvent.click(screen.getByRole('button', { name: '登入' }));

    await waitFor(() => {
      expect(userApi.login).toHaveBeenCalledWith({ email: 'a@example.com', password: 'correct-password' });
    });
  });
});
