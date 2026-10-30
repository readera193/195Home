import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AxiosError, AxiosHeaders } from 'axios';
import LoginPage from '../src/pages/LoginPage';
import * as userApi from '../src/services/userApi';
import * as notify from '../src/utils/notify';
import { AuthProvider } from '../src/hooks/useAuth';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../src/services/userApi');
vi.mock('../src/utils/notify', () => ({
  notifyError: vi.fn(() => Promise.resolve()),
}));

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.unstubAllEnvs();
    localStorage.clear();
  });

  it('後端未開啟 Demo 且未設定 LINE 時不顯示任何登入按鈕', async () => {
    vi.mocked(userApi.getAuthConfig).mockResolvedValue({ demoLoginEnabled: false });

    renderLoginPage();

    await waitFor(() => expect(userApi.getAuthConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByText('目前沒有可用的登入方式')).toBeInTheDocument();
  });

  it('設定 VITE_LINE_CHANNEL_ID 時顯示 LINE 登入按鈕', async () => {
    vi.stubEnv('VITE_LINE_CHANNEL_ID', '12345');
    vi.mocked(userApi.getAuthConfig).mockResolvedValue({ demoLoginEnabled: false });

    renderLoginPage();

    expect(await screen.findByRole('button', { name: '使用 LINE 登入' })).toBeInTheDocument();
  });

  it('Demo 開啟時點擊按鈕即以 loginAsDemo 登入並儲存 token', async () => {
    vi.mocked(userApi.getAuthConfig).mockResolvedValue({ demoLoginEnabled: true });
    vi.mocked(userApi.loginAsDemo).mockResolvedValue({
      token: 'jwt-token',
      userId: 1,
      displayName: 'Demo-ab12',
      expiresAt: '2026-01-01T00:00:00Z',
    });

    renderLoginPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Demo 體驗（免登入）' }));

    await waitFor(() => expect(localStorage.getItem('family195home_token')).toBe('jwt-token'));
  });

  it('Demo 登入失敗時顯示錯誤訊息', async () => {
    vi.mocked(userApi.getAuthConfig).mockResolvedValue({ demoLoginEnabled: true });
    // 後端錯誤格式為 RFC 9457 Problem Details：訊息在 detail，錯誤代碼在 code
    const config = { headers: new AxiosHeaders() };
    vi.mocked(userApi.loginAsDemo).mockRejectedValue(
      new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
        data: { status: 404, title: 'Not Found', detail: 'Demo 體驗未開啟', code: 'DEMO_LOGIN_DISABLED' },
        status: 404,
        statusText: '',
        headers: {},
        config,
      }),
    );

    renderLoginPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Demo 體驗（免登入）' }));

    await waitFor(() => expect(notify.notifyError).toHaveBeenCalledWith(expect.stringContaining('Demo 體驗未開啟')));
  });
});
