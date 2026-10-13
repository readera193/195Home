import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginCallbackPage from '../src/pages/LoginCallbackPage';
import * as userApi from '../src/services/userApi';
import { AuthProvider } from '../src/hooks/useAuth';

vi.mock('../src/services/userApi');

function renderCallback(query: string) {
  return render(
    <MemoryRouter initialEntries={[`/login/callback${query}`]}>
      <AuthProvider>
        <LoginCallbackPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginCallbackPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('state 相符時以授權碼呼叫 loginWithLine 並儲存 token', async () => {
    sessionStorage.setItem('family195home_line_state', 'abc');
    vi.mocked(userApi.loginWithLine).mockResolvedValue({
      token: 'jwt-token',
      userId: 1,
      displayName: '小明',
      expiresAt: '2026-01-01T00:00:00Z',
    });

    renderCallback('?code=the-code&state=abc');

    await waitFor(() => expect(userApi.loginWithLine).toHaveBeenCalledWith('the-code'));
    await waitFor(() => expect(localStorage.getItem('family195home_token')).toBe('jwt-token'));
  });

  it('state 不符時顯示錯誤且不呼叫 loginWithLine', async () => {
    sessionStorage.setItem('family195home_line_state', 'abc');

    renderCallback('?code=the-code&state=evil');

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('state 不符'));
    expect(userApi.loginWithLine).not.toHaveBeenCalled();
  });

  it('使用者取消授權（error 參數）時顯示錯誤', async () => {
    renderCallback('?error=access_denied&state=abc');

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('已取消'));
    expect(userApi.loginWithLine).not.toHaveBeenCalled();
  });
});
