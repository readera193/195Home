import React from 'react';
import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from '../src/hooks/useAuth';

function SessionControls() {
  const auth = useAuth();
  return <>
    <span>{auth.isAuthenticated ? '已登入' : '未登入'}</span>
    <button onClick={() => auth.login('user-b-token')}>登入 B</button>
    <button onClick={auth.logout}>登出</button>
  </>;
}

describe('切換使用者的資料隔離', () => {
  it('登出及重新登入都移除前一位使用者的家庭與支出快取', () => {
    localStorage.setItem('family195home_token', 'user-a-token');
    const client = new QueryClient();
    client.setQueryData(['myFamily'], { familyGroupId: 10, inviteCode: 'PRIVATE-A' });
    client.setQueryData(['expenses', 10], { items: [{ note: 'A 的支出' }] });
    render(<QueryClientProvider client={client}><AuthProvider><SessionControls /></AuthProvider></QueryClientProvider>);

    fireEvent.click(screen.getByText('登出'));
    expect(screen.getByText('未登入')).toBeInTheDocument();
    expect(client.getQueryData(['myFamily'])).toBeUndefined();
    expect(client.getQueryData(['expenses', 10])).toBeUndefined();

    client.setQueryData(['myFamily'], { familyGroupId: 10 });
    fireEvent.click(screen.getByText('登入 B'));
    expect(client.getQueryData(['myFamily'])).toBeUndefined();
    expect(localStorage.getItem('family195home_token')).toBe('user-b-token');
    localStorage.clear();
  });
});
