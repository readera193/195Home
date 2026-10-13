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

  it('顯示錯誤訊息，當登入失敗時', async () => {
    // 後端錯誤格式為 RFC 9457 Problem Details：訊息在 detail，錯誤代碼在 code
    const config = { headers: new AxiosHeaders() };
    vi.mocked(userApi.loginWithLine).mockRejectedValue(
      new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
        data: { status: 400, title: 'Bad Request', detail: '登入失敗', code: 'LOGIN_FAILED' },
        status: 400,
        statusText: '',
        headers: {},
        config,
      }),
    );

    renderLoginPage();

    fireEvent.change(screen.getByLabelText('LINE 暱稱'), { target: { value: '小明' } });
    fireEvent.click(screen.getByRole('button', { name: '使用 LINE 登入' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('登入失敗');
    });
  });

  it('登入成功時以輸入的暱稱呼叫 loginWithLine', async () => {
    vi.mocked(userApi.loginWithLine).mockResolvedValue({
      token: 'jwt-token',
      userId: 1,
      displayName: '小明',
      expiresAt: '2026-01-01T00:00:00Z',
    });

    renderLoginPage();

    fireEvent.change(screen.getByLabelText('LINE 暱稱'), { target: { value: ' 小明 ' } });
    fireEvent.click(screen.getByRole('button', { name: '使用 LINE 登入' }));

    await waitFor(() => {
      expect(userApi.loginWithLine).toHaveBeenCalledWith('小明');
    });
  });
});
